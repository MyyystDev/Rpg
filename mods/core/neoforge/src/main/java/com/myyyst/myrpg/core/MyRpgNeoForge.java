package com.myyyst.myrpg.core;

import com.myyyst.myrpg.core.command.RpgCommands;
import com.myyyst.myrpg.core.data.CoreData;
import com.myyyst.myrpg.core.event.RpgEvents;
import com.myyyst.myrpg.core.stat.PlayerStatTicker;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(Constants.MOD_ID)
public class MyRpgNeoForge {
    public MyRpgNeoForge(IEventBus eventBus) {
        Constants.LOG.info("Hello NeoForge world!");
        MyRpgCommon.init();

        NeoForge.EVENT_BUS.register(GameEvents.class);
    }

    /** Game-bus events, forwarded into common code. */
    public static class GameEvents {

        @SubscribeEvent
        public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
            event.addListener(
                    Identifier.fromNamespaceAndPath(Constants.MOD_ID, "stats"),
                    CoreData.STATS);
            event.addListener(
                    Identifier.fromNamespaceAndPath(Constants.MOD_ID, "conditions"),
                    CoreData.NAMED_CONDITIONS);
        }

        @SubscribeEvent
        public static void onServerTick(ServerTickEvent.Post event) {
            PlayerStatTicker.tick(event.getServer());
        }

        @SubscribeEvent
        public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                PlayerStatTicker.onJoin(player);
                RpgEvents.post(new RpgEvents.GameEvent(RpgEvents.PLAYER_JOIN, player, null));
            }
        }

        @SubscribeEvent
        public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                PlayerStatTicker.onRespawn(player);
                RpgEvents.post(new RpgEvents.GameEvent(RpgEvents.PLAYER_RESPAWN, player, null));
            }
        }

        @SubscribeEvent
        public static void onLivingDeath(LivingDeathEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                RpgEvents.post(new RpgEvents.GameEvent(RpgEvents.PLAYER_DEATH, player, null));
            }
            if (event.getSource().getEntity() instanceof ServerPlayer killer
                    && event.getEntity() != killer) {
                RpgEvents.post(new RpgEvents.GameEvent(RpgEvents.PLAYER_KILL, killer, event.getEntity()));
            }
        }
    }
}