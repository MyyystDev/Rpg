package com.myyyst.myrpg.entities;

import com.myyyst.myrpg.entities.ai.AiGoals;
import com.myyyst.myrpg.entities.ai.Targets;

public final class MyrpgEntities {

    public static final String MOD_ID = "myrpg_entities";

    public static void init() {
        AiGoals.init();
        Targets.init();
        // Reload-listener registration for EntitiesData.ENTITIES happens per
        // loader (keep the archetype-era lines in each entrypoint).
    }

    private MyrpgEntities() {}
}