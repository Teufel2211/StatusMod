package com.teufel.statusmod.registry;

public final class ModRegistries {
    private ModRegistries() {}

    public static void init() {
        ModItems.init();
        ModBlocks.init();
    }
}
