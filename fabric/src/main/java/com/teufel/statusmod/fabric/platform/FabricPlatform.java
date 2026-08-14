package com.teufel.statusmod.fabric.platform;

import com.teufel.statusmod.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricPlatform implements Platform {
    @Override
    public boolean isDedicatedServer() {
        try {
            return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getName() {
        return "fabric";
    }
}
