package com.teufel.statusmod.neoforge.platform;

import com.teufel.statusmod.platform.Platform;

public final class NeoForgePlatform implements Platform {
    @Override
    public void registerItem(String id) {
    }

    @Override
    public void registerBlock(String id) {
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return true;
    }

    @Override
    public String getName() {
        return "neoforge";
    }
}
