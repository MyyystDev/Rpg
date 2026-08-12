package com.myyyst.myrpg.entities;

import com.myyyst.myrpg.entities.ai.AiGoals;
import com.myyyst.myrpg.entities.ai.Targets;
import com.myyyst.myrpg.entities.command.EntityCommands;

public final class MyrpgEntities {

    public static final String MOD_ID = "myrpg_entities";

    public static void init() {
        AiGoals.init();
        Targets.init();
        EntityCommands.init();
        // Reload-listener registration for EntitiesData.ENTITIES happens per
        // loader in each entrypoint.
    }

    private MyrpgEntities() {}
}
