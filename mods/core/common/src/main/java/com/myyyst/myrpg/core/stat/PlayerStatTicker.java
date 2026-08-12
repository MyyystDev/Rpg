package com.myyyst.myrpg.core.stat;

import com.myyyst.myrpg.core.data.CoreData;
import com.myyyst.myrpg.core.data.StatDef;
import com.myyyst.myrpg.core.network.RpgPayloads;
import com.myyyst.myrpg.core.platform.Services;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** Drives StatEngine for all online players; called by loader tick hooks. */
public final class PlayerStatTicker {

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            StatStore store = PlayerStats.get(player);
            StatEngine.tick(store, player);
            syncDirty(player, store);
        }
    }

    // in PlayerStatTicker or a new PersistenceRules:
    public static void onRespawn(ServerPlayer player) {
        StatStore store = PlayerStats.get(player);
        for (Identifier statId : new ArrayList<>(store.all().keySet())) {
            CoreData.STATS.get(statId).ifPresent(def ->
                    def.persistence().ifPresent(p -> {
                        if (!p.keepOnDeath() || p.resetOnRespawn()) {
                            store.set(player, statId, def.value().defaultValue());
                        }
                    }));
        }
        store.reapplyStages(player);
        PlayerStats.markDirty(player);
        syncFull(player, store);
    }

    private static void syncFull(ServerPlayer player, StatStore store) {
        List<RpgPayloads.StatEntry> entries = new ArrayList<>();
        for (var e : CoreData.STATS.all().entrySet()) {
            StatDef def = e.getValue();
            def.hud().filter(StatDef.Hud::visible).ifPresent(hud ->
                    entries.add(entry(e.getKey(), store.get(e.getKey()), def, hud)));
        }
        if (!entries.isEmpty()) {
            Services.NETWORK.sendToPlayer(player, new RpgPayloads.SyncStats(entries));
        }
    }

    /** On join: restore stage effects without replaying enter/exit events. */
    public static void onJoin(ServerPlayer player) {
        StatStore store = PlayerStats.get(player);
        store.reapplyStages(player);
        syncFull(player, store);
    }

    private static void syncDirty(ServerPlayer player, StatStore store) {
        var dirty = store.drainDirty();
        if (dirty.isEmpty()) return;
        List<RpgPayloads.StatEntry> entries = new ArrayList<>();
        for (Identifier statId : dirty) {
            CoreData.STATS.get(statId).ifPresent(def ->
                    def.hud().filter(StatDef.Hud::visible).ifPresent(hud ->
                            entries.add(entry(statId, store.get(statId), def, hud))));
        }
        if (!entries.isEmpty()) {
            Services.NETWORK.sendToPlayer(player, new RpgPayloads.SyncStats(entries));
        }
    }

    private static RpgPayloads.StatEntry entry(Identifier statId, double value,
                                               StatDef def, StatDef.Hud hud) {
        String name = def.display().flatMap(StatDef.Display::name).orElse(statId.getPath());
        String color = def.display().flatMap(StatDef.Display::color).orElse("#FFFFFF");
        String icon = def.display().flatMap(StatDef.Display::icon)
                .map(Identifier::toString).orElse("");
        return new RpgPayloads.StatEntry(statId, value,
                def.value().min(), def.value().max(), def.value().defaultValue(),
                name, color, hud.type(), hud.visibility(),
                hud.visibilityValue().orElse(0.0), hud.showValue(), icon);
    }



    private PlayerStatTicker() {}
}