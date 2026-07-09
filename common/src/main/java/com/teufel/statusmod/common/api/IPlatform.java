package com.teufel.statusmod.common.api;

/**
 * Platform abstraction implemented by adapters.
 */
public interface IPlatform {
    IPlayer wrapPlayer(Object mcPlayer);
    Object getNativePlatform();
    
    /**
     * Optional feature-detection hook. Adapters may override to report support for specific features.
     */
    default boolean supportsFeature(String feature) { return false; }

    /**
     * Priority for selection when multiple adapters are present. Higher wins.
     */
    default int priority() { return 0; }
}
