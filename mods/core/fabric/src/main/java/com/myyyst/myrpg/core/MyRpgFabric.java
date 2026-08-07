package com.myyyst.myrpg.core;

import com.myyyst.myrpg.core.command.RpgCommands;
import com.myyyst.myrpg.core.data.CoreData;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.Identifier;
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
