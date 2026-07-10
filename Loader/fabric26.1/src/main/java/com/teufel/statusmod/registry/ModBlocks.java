package com.teufel.statusmod.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
    public static final Block STATUS_BLOCK = new Block(BlockBehaviour.Properties.of());
    public static final Item STATUS_BLOCK_ITEM = new Item(new Item.Properties());

    private ModBlocks() {}

    public static void init() {
        // Registration is handled by the loader-specific entry points for this target.
    }
}
