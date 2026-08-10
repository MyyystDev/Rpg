package com.myyyst.myrpg.core.stat;

import com.myyyst.myrpg.core.data.CoreData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;

/** Drives StatEngine for all online players; called by loader tick hooks. */
public final class PlayerStatTicker {

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            StatStore store = PlayerStats.get(player);
            if (!store.isEmpty()) {
                StatEngine.tick(store, player);
            }
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
    }

    /** On join: restore stage effects without replaying enter/exit events. */
    public static void onJoin(ServerPlayer player) {
        PlayerStats.get(player).reapplyStages(player);
    }

    private PlayerStatTicker() {}
}