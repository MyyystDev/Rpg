package com.myyyst.myrpg.core;

import com.myyyst.myrpg.core.client.ClientEffectCache;
import com.myyyst.myrpg.core.client.ClientStatCache;
import com.myyyst.myrpg.core.client.EffectHudOverlay;
import com.myyyst.myrpg.core.client.EffectEditorClient;
import com.myyyst.myrpg.core.client.StatEditorClient;
import com.myyyst.myrpg.core.client.StatHudOverlay;
import com.myyyst.myrpg.core.client.editor.ClientEditorNet;
import com.myyyst.myrpg.core.network.RpgPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

/**
 * Fabric client entry point: packet receivers, HUD elements, and the client caches.
 *
 * <p>Client-only code lives here rather than in {@code MyRpgFabric} because a dedicated
 * server never loads it. Note the payload <em>types</em> are registered on the common side
 * in {@code MyRpgFabric}; this class only attaches handlers to them.</p>
 */
public class MyRpgFabricClient implements ClientModInitializer {
    /** Called once by Fabric on the client. */
    @Override
    public void onInitializeClient() {

        // Give the common-module editor screens a way to send packets.
        ClientEditorNet.sender = ClientPlayNetworking::send;

        // Leaving a world must not carry its HUD state into the next one.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientStatCache.clear();
            ClientEffectCache.clear();
        });

        // Every receiver hops back onto the client thread before touching the caches.
        ClientPlayNetworking.registerGlobalReceiver(
                RpgPayloads.SyncStats.TYPE,
                (payload, context) ->
                        context.client().execute(() -> ClientStatCache.accept(payload))
        );

        ClientPlayNetworking.registerGlobalReceiver(
                RpgPayloads.SyncEffects.TYPE,
                (payload, context) ->
                        context.client().execute(() -> ClientEffectCache.accept(payload))
        );

        // Local countdown between server syncs; frozen while the game is paused so a
        // singleplayer pause does not eat the remaining duration.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && !client.isPaused()) ClientEffectCache.tick();
        });

        ClientPlayNetworking.registerGlobalReceiver(
                RpgPayloads.OpenStatEditor.TYPE,
                (payload, context) ->
                        context.client().execute(() -> StatEditorClient.open(payload))
        );

        ClientPlayNetworking.registerGlobalReceiver(
                RpgPayloads.OpenEffectEditor.TYPE,
                (payload, context) ->
                        context.client().execute(() -> EffectEditorClient.open(payload))
        );

        // Draw both overlays right after the boss bar, so they sit below it on screen.
        // in MyrpgCoreFabricClient.onInitializeClient():
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.BOSS_BAR,
                Identifier.fromNamespaceAndPath(Constants.MOD_ID, "stat_hud"),
                (graphics, tickCounter) -> StatHudOverlay.render(graphics));

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.BOSS_BAR,
                Identifier.fromNamespaceAndPath(Constants.MOD_ID, "effect_hud"),
                (graphics, tickCounter) -> EffectHudOverlay.render(graphics));
    }
}
