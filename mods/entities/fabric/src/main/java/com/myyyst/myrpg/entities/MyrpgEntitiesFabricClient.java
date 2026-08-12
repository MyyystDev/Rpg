package com.myyyst.myrpg.entities;

import com.myyyst.myrpg.entities.client.RpgEntityRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class MyrpgEntitiesFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(MyrpgEntitiesFabric.RPG_ENTITY, RpgEntityRenderer::new);
    }
}
