package com.teufel.statusmod.fabric.platform;

import com.teufel.statusmod.platform.Platform;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricPlatform implements Platform {
    @Override
    public void registerItem(String id) {
    }

    @Override
    public void registerBlock(String id) {
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        try {
            return FabricLoader.getInstance().isDevelopmentEnvironment();
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    public String getName() {
        return "fabric";
    }
}
