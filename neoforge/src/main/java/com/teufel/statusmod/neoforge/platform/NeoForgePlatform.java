package com.teufel.statusmod.neoforge.platform;

import com.teufel.statusmod.platform.Platform;

public final class NeoForgePlatform implements Platform {
    @Override
    public boolean isDedicatedServer() {
        try {
            Class<?> envClass = Class.forName("net.neoforged.fml.loading.FMLEnvironment");
            java.lang.reflect.Field distField = envClass.getField("dist");
            Object dist = distField.get(null);
            return dist != null && dist.toString().equals("DEDICATED_SERVER");
        } catch (Exception ignored) {}
        return false;
    }

    @Override
    public String getName() {
        return "neoforge";
    }
}
