package com.teufel.statusmod.registry;

import net.minecraft.world.item.Item;

public final class ModItems {
    public static final Item STATUS_TOKEN = new Item(new Item.Properties());

    private ModItems() {}

    public static void init() {
        // Registration is handled by the loader-specific entry points for this target.
    }
}
