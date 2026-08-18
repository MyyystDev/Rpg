package com.myyyst.myrpg.entities;

import com.myyyst.myrpg.entities.client.RpgEntityRenderer;
import com.myyyst.myrpg.entities.client.editor.EntityBrowserScreen;
import com.myyyst.myrpg.entities.network.EntitiesPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/**
 * Fabric client entry point for the entities mod: the entity renderer and the two
 * editor-opening packet handlers.
 */
public class MyrpgEntitiesFabricClient implements ClientModInitializer {

    /** Called once by Fabric on the client. */
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(MyrpgEntitiesFabric.RPG_ENTITY, RpgEntityRenderer::new);
        // Both receivers hop back onto the client thread before opening a screen.

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
