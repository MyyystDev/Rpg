package com.myyyst.myrpg.entities.client;

import com.myyyst.myrpg.entities.MyrpgEntitiesNeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public class EntitiesClientEvents {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MyrpgEntitiesNeoForge.RPG_ENTITY.get(), RpgEntityRenderer::new);
    }
}
