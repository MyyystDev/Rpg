package com.myyyst.myrpg.core;

public final class RpgDebug {

    private static volatile boolean enabled = false;

    public static boolean enabled() {
        return enabled;
    }

    public static void set(boolean value) {
        enabled = value;
        Constants.LOG.info("[myrpg] Debug mode {}", value ? "ENABLED" : "disabled");
    }

    private RpgDebug() {}
}