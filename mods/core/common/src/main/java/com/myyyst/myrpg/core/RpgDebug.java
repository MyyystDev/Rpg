package com.myyyst.myrpg.core;

/**
 * Global on/off switch for verbose RPG logging, toggled in game with {@code /myrpg debug on|off}
 * (see {@code MyRpgCommon#init}).
 *
 * <p>Rule engines check {@link #enabled()} before logging anything expensive, so leaving this
 * off costs almost nothing at runtime.</p>
 */
public final class RpgDebug {

    /** volatile: written from the server command thread, read from client/render threads. */
    private static volatile boolean enabled = false;

    /** @return true while debug logging is turned on. */
    public static boolean enabled() {
        return enabled;
    }

    /** Turns debug logging on or off and echoes the new state to the log. */
    public static void set(boolean value) {
        enabled = value;
        Constants.LOG.info("[myrpg] Debug mode {}", value ? "ENABLED" : "disabled");
    }

    /** Static-only helper: never instantiated. */
    private RpgDebug() {}
}