package com.myyyst.myrpg.entities;

import com.myyyst.myrpg.entities.client.RpgEntityRenderer;
import com.myyyst.myrpg.entities.client.editor.EntityBrowserScreen;
import com.myyyst.myrpg.entities.network.EntitiesPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class MyrpgEntitiesFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(MyrpgEntitiesFabric.RPG_ENTITY, RpgEntityRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(
                EntitiesPayloads.OpenEntityBrowser.TYPE,
                (payload, context) ->
                        context.client().execute(() -> EntityBrowserScreen.open(payload)));

        ClientPlayNetworking.registerGlobalReceiver(
                EntitiesPayloads.OpenEntityEditor.TYPE,
                (payload, context) ->
                        context.client().execute(() -> EntityBrowserScreen.openFocused(payload)));
    }
}
