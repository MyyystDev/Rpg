package com.myyyst.myrpg.core;

import com.myyyst.myrpg.core.client.ClientEvents;
import com.myyyst.myrpg.core.client.ClientEffectCache;
import com.myyyst.myrpg.core.client.ClientStatCache;
import com.myyyst.myrpg.core.client.EffectEditorClient;
import com.myyyst.myrpg.core.client.StatEditorClient;
import com.myyyst.myrpg.core.client.editor.ClientEditorNet;
import com.myyyst.myrpg.core.command.RpgCommands;
import com.myyyst.myrpg.core.data.CoreData;
import com.myyyst.myrpg.core.editor.EditorNet;
import com.myyyst.myrpg.core.effect.EffectManager;
import com.myyyst.myrpg.core.event.RpgEvents;
import com.myyyst.myrpg.core.network.RpgPayloads;
import com.myyyst.myrpg.core.stat.PlayerStatTicker;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * NeoForge entry point for the core mod - the counterpart of {@code MyRpgFabric}.
 *
 * <p>NeoForge splits events across two buses, and the distinction matters throughout this
 * class: the <em>mod bus</em> (the constructor's {@code eventBus}) carries setup events like
 * payload registration, while the <em>game bus</em> ({@code NeoForge.EVENT_BUS}) carries
 * runtime events like ticks and deaths.</p>
 */
@Mod(Constants.MOD_ID)
public class MyRpgNeoForge {
    /** Called by FML with this mod's own event bus. */
    public MyRpgNeoForge(IEventBus eventBus) {
        Constants.LOG.info("Hello NeoForge world!");
        MyRpgCommon.init();   // registries + commands, shared with Fabric

        eventBus.addListener(MyRpgNeoForge::onRegisterPayloads);   // mod bus: setup

        // Client-only wiring. The dist check keeps a dedicated server from ever touching
        // client classes, which would fail to load there.
        if (net.neoforged.fml.loading.FMLEnvironment.getDist() == Dist.CLIENT) {
            ClientEditorNet.sender = ClientPacketDistributor::sendToServer;
            eventBus.register(ClientEvents.class);              // mod bus: HUD registration
            NeoForge.EVENT_BUS.register(ClientEvents.Game.class); // game bus: ticks, logout
        }

        NeoForge.EVENT_BUS.register(GameEvents.class);   // game bus: runtime events
    }

    /**
     * Registers every packet type together with its handler.
     * Unlike Fabric, NeoForge takes codec and handler in one call; "1" is the protocol
     * version, which clients must match to connect.
     */
    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // ---- Clientbound (server -> client) ----

        // enqueueWork hops from the network thread onto the game thread before touching state.
        registrar.playToClient(
                RpgPayloads.SyncStats.TYPE,
                RpgPayloads.SyncStats.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientStatCache.accept(payload)));
        registrar.playToClient(RpgPayloads.OpenStatEditor.TYPE, RpgPayloads.OpenStatEditor.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> StatEditorClient.open(payload)));
        registrar.playToClient(RpgPayloads.SyncEffects.TYPE, RpgPayloads.SyncEffects.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientEffectCache.accept(payload)));
        registrar.playToClient(RpgPayloads.OpenEffectEditor.TYPE, RpgPayloads.OpenEffectEditor.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> EffectEditorClient.open(payload)));

        // ---- Serverbound (client -> server) ----

        registrar.playToServer(RpgPayloads.SaveStat.TYPE, RpgPayloads.SaveStat.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) EditorNet.handleSave(sp, payload);
                }));
        registrar.playToServer(RpgPayloads.DeleteStat.TYPE, RpgPayloads.DeleteStat.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) EditorNet.handleDelete(sp, payload);
                }));
        registrar.playToServer(RpgPayloads.SaveEffect.TYPE, RpgPayloads.SaveEffect.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) EditorNet.handleSaveEffect(sp, payload);
                }));
        registrar.playToServer(RpgPayloads.DeleteEffect.TYPE, RpgPayloads.DeleteEffect.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) EditorNet.handleDeleteEffect(sp, payload);
                }));
    }

    /**
     * Game-bus events, forwarded into common code.
     * Every handler here mirrors one in {@code MyRpgFabric}, so behaviour stays identical
     * across loaders.
     */
    public static class GameEvents {

        /** Hooks the datapack loaders into /reload; without this CoreData stays empty. */
        @SubscribeEvent
        public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
            event.addListener(
                    Identifier.fromNamespaceAndPath(Constants.MOD_ID, "stats"),
                    CoreData.STATS);
            event.addListener(
                    Identifier.fromNamespaceAndPath(Constants.MOD_ID, "conditions"),
                    CoreData.NAMED_CONDITIONS);
            event.addListener(
                    Identifier.fromNamespaceAndPath(Constants.MOD_ID, "effects"),
                    CoreData.EFFECTS);
        }

        /** The server heartbeat: stat rules, effect ticking, client syncs. */
        @SubscribeEvent
        public static void onServerTick(ServerTickEvent.Post event) {
            PlayerStatTicker.tick(event.getServer());
        }

        /** Join: rebuild stage effects and push a full HUD sync, then post the event. */
        @SubscribeEvent
        public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                PlayerStatTicker.onJoin(player);
                RpgEvents.post(new RpgEvents.GameEvent(RpgEvents.PLAYER_JOIN, player, null));
            }
        }

        /** Respawn: apply each stat's persistence rules to the new player entity. */
        @SubscribeEvent
        public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                PlayerStatTicker.onRespawn(player);
                RpgEvents.post(new RpgEvents.GameEvent(RpgEvents.PLAYER_RESPAWN, player, null));
            }
        }

        // Custom-effect restrictions: can_attack / can_use_items

        /** Blocks attacking while a restriction effect is active. */
        @SubscribeEvent
        public static void onAttackEntity(AttackEntityEvent event) {
            if (event.getEntity() instanceof ServerPlayer player
                    && EffectManager.isRestricted(player, "attack")) {
                event.setCanceled(true);
            }
        }

        /** Blocks item use while a restriction effect is active. */
        @SubscribeEvent
        public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
            if (event.getEntity() instanceof ServerPlayer player
                    && EffectManager.isRestricted(player, "use_items")) {
                event.setCanceled(true);
            }
        }

        /** Death: one death event for the victim, plus a kill event when a player did it. */
        @SubscribeEvent
        public static void onLivingDeath(LivingDeathEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                EffectManager.onDeath(player);
                RpgEvents.post(new RpgEvents.GameEvent(RpgEvents.PLAYER_DEATH, player, null));
            }
            // getEntity() != killer excludes suicide from counting as a kill
            if (event.getSource().getEntity() instanceof ServerPlayer killer
                    && event.getEntity() != killer) {
                RpgEvents.post(new RpgEvents.GameEvent(RpgEvents.PLAYER_KILL, killer, event.getEntity()));
            }
        }
    }
}