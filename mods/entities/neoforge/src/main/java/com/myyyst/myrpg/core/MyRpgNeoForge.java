package com.myyyst.myrpg.core;

import com.myyyst.myrpg.core.command.RpgCommands;
import com.myyyst.myrpg.core.data.CoreData;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod(Constants.MOD_ID)
public class MyRpgNeoForge {
    public MyRpgNeoForge(IEventBus eventBus) {
        Constants.LOG.info("Hello NeoForge world!");
        MyRpgCommon.init();

        NeoForge.EVENT_BUS.register(GameEvents.class);
    }

    public static class GameEvents {
        @SubscribeEvent
        public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
            event.addListener(
                    Identifier.fromNamespaceAndPath(Constants.MOD_ID, "stats"),
                    CoreData.STATS);
        }

        @SubscribeEvent
        public static void onRegisterCommands(RegisterCommandsEvent event) {
            RpgCommands.register(event.getDispatcher());
        }
    }
}