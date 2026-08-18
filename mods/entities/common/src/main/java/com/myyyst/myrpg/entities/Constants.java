package com.myyyst.myrpg.entities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared constants for the entities mod - the twin of the core mod's {@code Constants},
 * with its own namespace so the two mods never collide over resource ids.
 */
public final class Constants {
    /** Namespace of this mod. Must match the id in fabric.mod.json / neoforge.mods.toml. */
    public static final String MOD_ID = "myrpg_entities";
    /** Human readable name, only used for display and as the logger name. */
    public static final String MOD_NAME = "Myyyst RPG: Entities";
    /** Single logger shared by the whole entities module. */
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    /** Static-only holder: never instantiated. */
    private Constants() {}
}