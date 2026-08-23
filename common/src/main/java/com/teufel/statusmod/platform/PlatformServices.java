package com.teufel.statusmod.platform;

import java.util.ServiceLoader;

public final class PlatformServices {
    private static volatile Platform platform;

    private PlatformServices() {}

    public static Platform getPlatform() {
        Platform result = platform;
        if (result == null) {
            synchronized (PlatformServices.class) {
                result = platform;
                if (result == null) {
                    result = ServiceLoader.load(Platform.class)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No Platform implementation found"));
                    platform = result;
                }
            }
        }
        return result;
    }
}
