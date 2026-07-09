package com.teufel.statusmod.registry;

import com.teufel.statusmod.StatusMod;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(StatusMod.MOD_ID, Registries.ITEM);

    public static final RegistrySupplier<Item> STATUS_TOKEN =
        ITEMS.register("status_token", () -> new Item(new Item.Properties()));

    private ModItems() {}

    public static void init() {
        ITEMS.register();
    }
}
