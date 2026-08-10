package com.myyyst.myrpg.core;

import com.myyyst.myrpg.core.client.ClientStatCache;
import com.myyyst.myrpg.core.client.StatHudOverlay;
import com.myyyst.myrpg.core.network.RpgPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

public class MyRpgFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(RpgPayloads.SyncStats.TYPE,
                (payload, context) -> ClientStatCache.accept(payload));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientStatCache.clear());

        // in MyrpgCoreFabricClient.onInitializeClient():
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.BOSS_BAR,
                Identifier.fromNamespaceAndPath(Constants.MOD_ID, "stat_hud"),
                (graphics, tickCounter) -> StatHudOverlay.render(graphics));
    }
}
