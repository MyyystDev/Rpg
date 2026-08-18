package com.myyyst.myrpg.core.platform;

import com.myyyst.myrpg.core.platform.services.IPlatformHelper;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric implementation of {@code IPlatformHelper}.
 * Registered via {@code META-INF/services} and loaded by {@code Services.PLATFORM}.
 */
public class FabricPlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {

        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {

        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }
}
