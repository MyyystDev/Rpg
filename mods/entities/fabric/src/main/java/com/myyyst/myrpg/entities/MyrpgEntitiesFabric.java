package com.myyyst.myrpg.entities;

import net.fabricmc.api.ModInitializer;

public class MyrpgEntitiesFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        MyrpgEntities.init();
    }
}