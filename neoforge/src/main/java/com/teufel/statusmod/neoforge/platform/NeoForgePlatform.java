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
        try {
            Class<?> modListClass = Class.forName("net.neoforged.fml.ModList");
            java.lang.reflect.Method getMethod = modListClass.getMethod("get");
            Object modList = getMethod.invoke(null);
            if (modList != null) {
                java.lang.reflect.Method isDevMethod = modList.getClass().getMethod("isDevelopmentEnvironment");
                return (boolean) isDevMethod.invoke(modList);
            }
        } catch (Exception ignored) {}
        try {
            Class<?> loaderClass = Class.forName("net.neoforged.fml.loading.FMLLoader");
            java.lang.reflect.Method m = loaderClass.getMethod("isProduction");
            return !(boolean) m.invoke(null);
        } catch (Exception ignored) {}
        return true;
    }

    @Override
    public String getName() {
        return "neoforge";
    }
}
