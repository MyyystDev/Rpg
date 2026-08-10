package com.myyyst.myrpg.core;

import com.myyyst.myrpg.core.command.RpgCommands;
import com.myyyst.myrpg.core.data.CoreData;
import com.myyyst.myrpg.core.event.RpgEvents;
import com.myyyst.myrpg.core.stat.PlayerStatTicker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class MyRpgFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Constants.LOG.info("Hello Fabric world!");
        MyRpgCommon.init();

        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(new ReloadAdapter("stats", CoreData.STATS));

        ServerTickEvents.END_SERVER_TICK.register(PlayerStatTicker::tick);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                PlayerStatTicker.onJoin(handler.getPlayer()));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PlayerStatTicker.onJoin(handler.getPlayer());
            RpgEvents.post(new RpgEvents.GameEvent(RpgEvents.PLAYER_JOIN, handler.getPlayer(), null));
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                RpgEvents.post(new RpgEvents.GameEvent(RpgEvents.PLAYER_DEATH, player, null));
            }
            if (source.getEntity() instanceof ServerPlayer killer) {
                RpgEvents.post(new RpgEvents.GameEvent(RpgEvents.PLAYER_KILL, killer, entity));
            }
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            PlayerStatTicker.onJoin(newPlayer);   // re-apply stages on the fresh entity
            RpgEvents.post(new RpgEvents.GameEvent(RpgEvents.PLAYER_RESPAWN, newPlayer, null));
        });

        // ---- Commands ----
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, buildContext, selection) -> RpgCommands.register(dispatcher)
        );
    }

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
