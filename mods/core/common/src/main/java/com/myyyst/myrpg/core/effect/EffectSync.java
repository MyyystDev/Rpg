package com.myyyst.myrpg.core.effect;

import com.myyyst.myrpg.core.data.CoreData;
import com.myyyst.myrpg.core.data.EffectDefinition;
import com.myyyst.myrpg.core.network.RpgPayloads;
import com.myyyst.myrpg.core.platform.Services;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Server -> client HUD sync. Structural changes (apply/remove/stack) mark
 * the player; the ticker flushes one full-replace payload per change burst.
 * The client counts remaining ticks down locally between syncs.
 */
public final class EffectSync {

    /**
     * Players whose effect list changed since the last flush. Static and server-wide:
     * a burst of changes in one tick collapses into a single packet.
     */
    private static final Set<UUID> PENDING = new HashSet<>();

    /** Queues a sync for the next flush. Called by EffectManager on every structural change. */
    public static void mark(ServerPlayer player) { PENDING.add(player.getUUID()); }

    /** Called by the player ticker; no-op unless marked. */
    public static void flush(ServerPlayer player) {
        if (PENDING.remove(player.getUUID())) send(player);
    }

    /** Unconditional full sync (join/respawn). */
    public static void send(ServerPlayer player) {
        PENDING.remove(player.getUUID());   // this send supersedes any queued one
        List<RpgPayloads.EffectEntry> entries = new ArrayList<>();
        for (EffectInstance instance : PlayerEffects.get(player).all()) {
            EffectDefinition def = CoreData.EFFECTS.get(instance.effectId).orElse(null);
            // undefined or explicitly hidden effects are never shown to the client
            if (def == null || def.displayOptions().hidden()) continue;
            entries.add(entry(instance, def));
        }
        Services.NETWORK.sendToPlayer(player, new RpgPayloads.SyncEffects(entries));
    }

    /**
     * Flattens definition + instance into the wire record the HUD draws, so the client
     * needs no datapack knowledge. Colour falls back to a per-category default.
     */
    private static RpgPayloads.EffectEntry entry(EffectInstance instance, EffectDefinition def) {
        String name = def.display().flatMap(EffectDefinition.Display::name)
                .orElse(instance.effectId.getPath());
        String color = def.display().flatMap(EffectDefinition.Display::color)
                .orElse(categoryColor(def.category()));
        String icon = def.display().flatMap(EffectDefinition.Display::icon)
                .map(Object::toString).orElse("");
        EffectDefinition.DisplayOptions options = def.displayOptions();
        return new RpgPayloads.EffectEntry(
                instance.effectId, name, color, icon, def.category(),
                instance.remaining, instance.level, instance.stacks,
                options.showIcon(), options.showDuration(),
                options.showStacks(), options.showLevel());
    }

    /** Default tint when a definition declares no colour: green good, red bad, grey neutral. */
    private static String categoryColor(String category) {
        return switch (category) {
            case "beneficial" -> "#58C85E";
            case "harmful" -> "#E05555";
            default -> "#A8A8B8";
        };
    }

    /** Static-only helper: never instantiated. */
    private EffectSync() {}
}
