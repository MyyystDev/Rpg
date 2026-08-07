package com.myyyst.myrpg.entities;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class MyrpgEntitiesNeoForge {
    public MyrpgEntitiesNeoForge(IEventBus modBus) {
        MyrpgEntities.init();
    }
}