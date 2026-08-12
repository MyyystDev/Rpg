package com.myyyst.myrpg.entities.data;

import com.myyyst.myrpg.core.data.RpgDataManager;

public final class EntitiesData {

    public static final RpgDataManager<EntityDefinition> ENTITIES =
            new RpgDataManager<>("myrpg/entities", EntityDefinition.CODEC, "entity definition");

    private EntitiesData() {}
}