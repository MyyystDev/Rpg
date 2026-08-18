package com.myyyst.myrpg.entities;

import com.myyyst.myrpg.entities.ai.AiGoals;
import com.myyyst.myrpg.entities.ai.Targets;
import com.myyyst.myrpg.entities.command.EntityCommands;

/**
 * Loader-independent entry point of the entities mod, mirroring {@code MyRpgCommon}.
 *
 * <p>Registers the AI goal and targeting type registries plus this module's commands.
 * As in core, this must run before any datapack is parsed.</p>
 */
public final class MyrpgEntities {

    /** Duplicate of {@code Constants.MOD_ID}, kept for callers that only import this class. */
    public static final String MOD_ID = "myrpg_entities";

    /** Registers content types and commands. Call once, early, from the loader entry point. */
    public static void init() {
        AiGoals.init();
        Targets.init();
        EntityCommands.init();
        // Reload-listener registration for EntitiesData.ENTITIES happens per
        // loader in each entrypoint.
    }

    /** Static-only entry point: never instantiated. */
    private MyrpgEntities() {}
}
