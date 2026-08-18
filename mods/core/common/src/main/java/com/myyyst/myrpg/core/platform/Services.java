package com.myyyst.myrpg.core.platform;

import com.myyyst.myrpg.core.Constants;
import com.myyyst.myrpg.core.platform.services.INetworkHelper;
import com.myyyst.myrpg.core.platform.services.IPlatformHelper;

import java.util.ServiceLoader;

/**
 * Bridge from the loader-agnostic {@code common} code to the loader-specific implementations.
 *
 * <p>The common module cannot reference Fabric or NeoForge classes directly, so every
 * loader-dependent operation goes through an interface here. Each loader module ships an
 * implementation and declares it in {@code src/main/resources/META-INF/services/&lt;interface&gt;};
 * the JDK {@link ServiceLoader} then picks whichever one is on the classpath at runtime.</p>
 */
public class Services {

    /** Loader/environment queries (platform name, dev vs production, is-mod-loaded). */
    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);
    /** Packet sending, since Fabric and NeoForge have incompatible networking APIs. */
    public static final INetworkHelper NETWORK = load(INetworkHelper.class);

    /**
     * Resolves the single implementation of {@code clazz} provided by the running loader.
     *
     * @throws NullPointerException if no implementation is registered - that means the
     *         loader module forgot its {@code META-INF/services} entry.
     */
    public static <T> T load(Class<T> clazz) {

        final T loadedService = ServiceLoader.load(clazz, Services.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        Constants.LOG.debug("Loaded {} for service {}", loadedService, clazz);
        return loadedService;
    }
}