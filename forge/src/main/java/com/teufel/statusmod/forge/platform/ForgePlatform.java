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
        try {
            Class<?> loaderClass = Class.forName("net.minecraftforge.fml.loading.FMLLoader");
            java.lang.reflect.Method m = loaderClass.getMethod("isProduction");
            return !(boolean) m.invoke(null);
        } catch (Exception ignored) {}
        return true;
    }

    @Override
    public String getName() {
        return "forge";
    }
}
