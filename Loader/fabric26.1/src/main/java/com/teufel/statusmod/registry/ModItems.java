package com.teufel.statusmod.registry;

import net.minecraft.world.item.Item;

public final class ModItems {
    public static Item STATUS_TOKEN;  // ← Nicht mehr initialisiert

    private ModItems() {}

    public static void init() {
        STATUS_TOKEN = new Item(new Item.Properties());  // ← Verzögerte Initialisierung
    }
}