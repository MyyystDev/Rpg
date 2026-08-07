package com.myyyst.myrpg.entities.data;

import com.myyyst.myrpg.core.data.RpgDataManager;

public final class EntitiesData {
    public static final RpgDataManager<EntityArchetype> ARCHETYPES =
            new RpgDataManager<>("myrpg/entities", EntityArchetype.CODEC, "entity archetype");

    private EntitiesData() {}
}