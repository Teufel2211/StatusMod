package com.teufel.statusmod.platform;

import java.util.ServiceLoader;

public final class PlatformServices {
    private static Platform platform;

    private PlatformServices() {}

    public static Platform getPlatform() {
        if (platform == null) {
            platform = ServiceLoader.load(Platform.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No Platform implementation found"));
        }
        return platform;
    }
}
