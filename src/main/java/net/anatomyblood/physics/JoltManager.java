package net.anatomyblood.physics;

import net.anatomyblood.util.BloodConstants;

/**
 * Physics manager stub. Currently the ragdoll uses a pure-Java solver.
 * This class is reserved for potential future native physics engine integration.
 */
public class JoltManager {
    private static boolean initialized = false;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        BloodConstants.LOGGER.info("Ragdoll physics solver initialized (pure-Java mode).");
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
