package com.myyyst.myrpg.core.stat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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

    /** On join: restore stage effects without replaying enter/exit events. */
    public static void onJoin(ServerPlayer player) {
        PlayerStats.get(player).reapplyStages(player);
    }

    private PlayerStatTicker() {}
}