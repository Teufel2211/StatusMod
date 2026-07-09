package com.teufel.statusmod.platform;

public interface Platform {
    void registerItem(String id);
    void registerBlock(String id);

    boolean isDevelopmentEnvironment();
    String getName();
}
