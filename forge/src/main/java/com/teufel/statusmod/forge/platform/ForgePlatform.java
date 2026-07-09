package com.teufel.statusmod.forge.platform;

import com.teufel.statusmod.platform.Platform;

public final class ForgePlatform implements Platform {
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
        return "forge";
    }
}
