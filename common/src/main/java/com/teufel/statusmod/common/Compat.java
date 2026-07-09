package com.teufel.statusmod.common;

import com.teufel.statusmod.common.api.IPlatform;

public final class Compat {
    private static IPlatform instance;

    private Compat() {}

    public static void init(IPlatform platform) {
        instance = platform;
    }

    public static IPlatform platform() {
        if (instance == null) {
            throw new IllegalStateException("Compat not initialized");
        }
        return instance;
    }
}
