package net.anatomyblood.physics;

import net.anatomyblood.util.BloodConstants;

public class JoltManager {
    private static boolean initialized = false;
    private static boolean nativeAvailable = false;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            // Attempt to load native Jolt C++ library
            try {
                System.loadLibrary("joltjni");
            } catch (Throwable ignored) {}

            com.github.stephengold.joltjni.Jolt.registerDefaultAllocator();
            com.github.stephengold.joltjni.Jolt.newFactory();
            com.github.stephengold.joltjni.Jolt.registerTypes();
            nativeAvailable = true;
            BloodConstants.LOGGER.info("Jolt Physics C++ native engine initialized successfully!");
        } catch (Throwable t) {
            nativeAvailable = false;
            BloodConstants.LOGGER.warn("Jolt C++ native library not loaded ({}), using high-performance Java Ragdoll Solver.", t.getMessage());
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean isNativeAvailable() {
        return nativeAvailable;
    }
}
