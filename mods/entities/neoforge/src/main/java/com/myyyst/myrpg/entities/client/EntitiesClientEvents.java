package com.myyyst.myrpg.entities.client;

import com.myyyst.myrpg.entities.MyrpgEntitiesNeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * NeoForge client mod-bus events for the entities mod.
 * Registered only on the client, from {@code MyrpgEntitiesNeoForge}'s constructor.
 */
public class EntitiesClientEvents {

    /** Binds the one entity type to {@code RpgEntityRenderer}. */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MyrpgEntitiesNeoForge.RPG_ENTITY.get(), RpgEntityRenderer::new);
    }
}
