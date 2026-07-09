package com.teufel.statusmod.registry;

import com.teufel.statusmod.StatusMod;
import dev.architectury.registry.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.item.Item;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(StatusMod.MOD_ID, Registries.BLOCK);
    public static final DeferredRegister<Item> BLOCK_ITEMS =
        DeferredRegister.create(StatusMod.MOD_ID, Registries.ITEM);

    static {
        BLOCKS.register("status_block", () -> new Block(BlockBehaviour.Properties.of()));
        BLOCK_ITEMS.register("status_block", () -> new Item(new Item.Properties()));
    }

    private ModBlocks() {}

    public static void init() {
        BLOCKS.register();
        BLOCK_ITEMS.register();
    }
}
