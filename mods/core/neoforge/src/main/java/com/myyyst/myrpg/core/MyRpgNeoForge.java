package com.myyyst.myrpg.core;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class MyRpgNeoForge {
    public MyRpgNeoForge(IEventBus eventBus) {
        Constants.LOG.info("Hello NeoForge world!");
        MyRpgCommon.init();
    }
}