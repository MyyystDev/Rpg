package com.myyyst.myrpg.core;

import com.myyyst.myrpg.core.command.RpgCommands;
import com.myyyst.myrpg.core.data.CoreData;
import com.myyyst.myrpg.core.editor.EditorNet;
import com.myyyst.myrpg.core.effect.EffectManager;
import com.myyyst.myrpg.core.event.RpgEvents;
import com.myyyst.myrpg.core.network.RpgPayloads;
import com.myyyst.myrpg.core.stat.PlayerStatTicker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Fabric entry point for the core mod.
 *
 * <p>This is the loader half of the split described in the README: {@code MyRpgCommon} owns
 * the loader-agnostic logic, and this class wires it into Fabric's APIs - packet types,
 * datapack reload listeners, the server tick, and the game events the rule engine consumes.
 * The NeoForge module does the same job through its own event bus.</p>
 */
public class MyRpgFabric implements ModInitializer {
    /** Called once by Fabric on both client and dedicated server. */
    @Override
    public void onInitialize() {
        Constants.LOG.info("Hello Fabric world!");
        MyRpgCommon.init();   // registries + commands, shared with NeoForge

        // Register payload codecs FIRST — a receiver for an unregistered type would throw.
        // clientbound = server to client, serverbound = client to server.
        PayloadTypeRegistry.clientboundPlay().register(
                RpgPayloads.SyncStats.TYPE,
                RpgPayloads.SyncStats.STREAM_CODEC
        );

        PayloadTypeRegistry.clientboundPlay().register(
                RpgPayloads.OpenStatEditor.TYPE,
                RpgPayloads.OpenStatEditor.STREAM_CODEC
        );

        PayloadTypeRegistry.clientboundPlay().register(
                RpgPayloads.SyncEffects.TYPE,
                RpgPayloads.SyncEffects.STREAM_CODEC
        );

        PayloadTypeRegistry.serverboundPlay().register(
                RpgPayloads.SaveStat.TYPE,
                RpgPayloads.SaveStat.STREAM_CODEC
        );

        PayloadTypeRegistry.serverboundPlay().register(
                RpgPayloads.DeleteStat.TYPE,
                RpgPayloads.DeleteStat.STREAM_CODEC
        );

        PayloadTypeRegistry.clientboundPlay().register(
                RpgPayloads.OpenEffectEditor.TYPE,
                RpgPayloads.OpenEffectEditor.STREAM_CODEC
        );

        PayloadTypeRegistry.serverboundPlay().register(
                RpgPayloads.SaveEffect.TYPE,
                RpgPayloads.SaveEffect.STREAM_CODEC
        );

        PayloadTypeRegistry.serverboundPlay().register(
                RpgPayloads.DeleteEffect.TYPE,
                RpgPayloads.DeleteEffect.STREAM_CODEC
        );

        // Register handlers only after their codecs
        registerServerReceivers();

        // Hook the datapack loaders into /reload; without this CoreData stays empty.
        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(
                        new ReloadAdapter("stats", CoreData.STATS)
                );

        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(
                        new ReloadAdapter("conditions", CoreData.NAMED_CONDITIONS)
                );

        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(
                        new ReloadAdapter("effects", CoreData.EFFECTS)
                );

        // The server heartbeat: stat rules, effect ticking, client syncs.
        ServerTickEvents.END_SERVER_TICK.register(
                PlayerStatTicker::tick
        );

        // Custom-effect restrictions: can_attack / can_use_items
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer sp
                    && EffectManager.isRestricted(sp, "attack")) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player instanceof ServerPlayer sp
                    && EffectManager.isRestricted(sp, "use_items")) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // Join: rebuild stage effects and push a full HUD sync, then post the event.
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> {
                    PlayerStatTicker.onJoin(handler.getPlayer());

                    RpgEvents.post(new RpgEvents.GameEvent(
                            RpgEvents.PLAYER_JOIN,
                            handler.getPlayer(),
                            null
                    ));
                }
        );

        // Death: one death event for the victim, plus a kill event when a player did it.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                EffectManager.onDeath(player);
                RpgEvents.post(new RpgEvents.GameEvent(
                        RpgEvents.PLAYER_DEATH,
                        player,
                        null
                ));
            }

            // entity != killer excludes suicide from counting as a kill
            if (source.getEntity() instanceof ServerPlayer killer
                    && entity != killer) {
                RpgEvents.post(new RpgEvents.GameEvent(
                        RpgEvents.PLAYER_KILL,
                        killer,
                        entity
                ));
            }
        });

        // Respawn hands out a brand new player entity, so state must be re-applied to it.
        ServerPlayerEvents.AFTER_RESPAWN.register(
                (oldPlayer, newPlayer, alive) -> {
                    PlayerStatTicker.onJoin(newPlayer);

                    RpgEvents.post(new RpgEvents.GameEvent(
                            RpgEvents.PLAYER_RESPAWN,
                            newPlayer,
                            null
                    ));
                }
        );

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, buildContext, selection) ->
                        RpgCommands.register(dispatcher)
        );
    }

    /**
     * Handlers for the editor's client-to-server packets.
     * Each hops back onto the server thread with {@code server().execute(...)} - packet
     * handlers run on the network thread, where touching game state is unsafe.
     */
    private static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(
                RpgPayloads.SaveStat.TYPE,
                (payload, context) -> context.server().execute(() ->
                        EditorNet.handleSave(context.player(), payload)
                )
        );

        ServerPlayNetworking.registerGlobalReceiver(
                RpgPayloads.DeleteStat.TYPE,
                (payload, context) -> context.server().execute(() ->
                        EditorNet.handleDelete(context.player(), payload)
                )
        );

        ServerPlayNetworking.registerGlobalReceiver(
                RpgPayloads.SaveEffect.TYPE,
                (payload, context) -> context.server().execute(() ->
                        EditorNet.handleSaveEffect(context.player(), payload)
                )
        );

        ServerPlayNetworking.registerGlobalReceiver(
                RpgPayloads.DeleteEffect.TYPE,
                (payload, context) -> context.server().execute(() ->
                        EditorNet.handleDeleteEffect(context.player(), payload)
                )
        );
    }

    /**
     * Wraps a plain {@code PreparableReloadListener} so Fabric can register it.
     *
     * <p>Fabric requires every reload listener to carry an identifier (for ordering and
     * dependencies), which the common-module {@code RpgDataManager} has no way to provide -
     * so this adapter attaches one and forwards the actual reload call.</p>
     */
    private static class ReloadAdapter implements IdentifiableResourceReloadListener {

        private final Identifier id;
        private final PreparableReloadListener listener;

        ReloadAdapter(String path, PreparableReloadListener listener) {
            this.id = Identifier.fromNamespaceAndPath(Constants.MOD_ID, path);
            this.listener = listener;
        }

        @Override
        public Identifier getFabricId() { return id; }

        @Override
        public CompletableFuture<Void> reload(PreparableReloadListener.SharedState sharedState,
                                              Executor backgroundExecutor,
                                              PreparableReloadListener.PreparationBarrier barrier,
                                              Executor gameExecutor) {
            return listener.reload(sharedState, backgroundExecutor, barrier, gameExecutor);
        }
    }
}
