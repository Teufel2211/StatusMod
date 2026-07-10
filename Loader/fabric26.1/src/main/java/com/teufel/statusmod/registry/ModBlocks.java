package com.teufel.statusmod.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
    public static Block STATUS_BLOCK;        // ← Nicht mehr initialisiert
    public static Item STATUS_BLOCK_ITEM;    // ← Nicht mehr initialisiert

    private ModBlocks() {}

    public static void init() {
        STATUS_BLOCK = new Block(BlockBehaviour.Properties.of());          // ← Verzögert
        STATUS_BLOCK_ITEM = new Item(new Item.Properties());               // ← Verzögert
    }
}