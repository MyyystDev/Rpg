package com.myyyst.myrpg.entities;

public final class MyrpgEntities {
    public static void init() {
        Constants.LOG.info("Initializing {}", Constants.MOD_NAME);
        // registrations into core's registries land here (has_tag, etc.)
    }
    private MyrpgEntities() {}
}