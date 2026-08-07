package com.myyyst.myrpg.core;

import net.fabricmc.api.ModInitializer;

public class MyRpgFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Constants.LOG.info("Hello Fabric world!");
        MyRpgCommon.init();
    }
}
