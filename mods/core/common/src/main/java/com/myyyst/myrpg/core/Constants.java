package com.myyyst.myrpg.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared constants for the core mod.
 *
 * <p>{@link #MOD_ID} is the namespace used everywhere an {@code Identifier} is built
 * (datapack folders, network payload ids, saved-data keys), so changing it renames
 * every resource the mod owns.</p>
 */
public class Constants {

    /** Namespace of this mod. Must match the id declared in fabric.mod.json / neoforge.mods.toml. */
    public static final String MOD_ID = "myrpg_core";
    /** Human readable name, only used for display and as the logger name. */
    public static final String MOD_NAME = "MyRpg Core";
    /** Single logger shared by the whole core module. */
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);
}