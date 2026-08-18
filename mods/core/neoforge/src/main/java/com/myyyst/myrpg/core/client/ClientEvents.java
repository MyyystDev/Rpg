package com.myyyst.myrpg.core.client;

import com.myyyst.myrpg.core.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * NeoForge client event handlers, split by bus.
 *
 * <p>The outer class holds mod-bus events (one-off setup such as registering HUD layers);
 * the nested {@link Game} class holds game-bus events (per-tick and connection events).
 * They are registered separately in {@code MyRpgNeoForge}'s constructor.</p>
 */
public class ClientEvents {

    /** Registers the two HUD overlays above the vanilla experience bar. */
    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_LEVEL,
                Identifier.fromNamespaceAndPath(Constants.MOD_ID, "stat_hud"),
                (graphics, deltaTracker) -> StatHudOverlay.render(graphics));
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_LEVEL,
                Identifier.fromNamespaceAndPath(Constants.MOD_ID, "effect_hud"),
                (graphics, deltaTracker) -> EffectHudOverlay.render(graphics));
    }

    /** Game-bus client events (mod-bus events stay in the outer class). */
    public static class Game {

        /**
         * Local effect countdown between server syncs. Frozen while paused, so a
         * singleplayer pause does not eat the remaining duration.
         */
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && !mc.isPaused()) ClientEffectCache.tick();
        }

        /** Leaving a world must not carry its HUD state into the next one. */
        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientStatCache.clear();
            ClientEffectCache.clear();
        }
    }
}