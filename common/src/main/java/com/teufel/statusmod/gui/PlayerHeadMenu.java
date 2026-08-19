package com.teufel.statusmod.gui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

public class PlayerHeadMenu extends AbstractContainerMenu {
    private final Container container;
    private final int displaySlots;
    private Map<Integer, String> slotUuids = new HashMap<>();

    public PlayerHeadMenu(int syncId, Inventory playerInventory, Container container) {
        super(MenuType.GENERIC_9x6, syncId);
        this.container = container;
        this.displaySlots = container.getContainerSize();
        checkContainerSize(container, 54);
        container.startOpen(playerInventory.player);

        if (container instanceof HeadContainer hc) {
            this.slotUuids = hc.slotUuids;
        }

        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(container, row * 9 + col, 8 + col * 18, 18 + row * 18) {
                    @Override
                    public boolean mayPickup(Player player) {
                        return false;
                    }
                });
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 184 + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 242));
        }
    }

    @Override
    public void clicked(int slotIndex, int button, ClickType clickType, Player player) {
        if (slotIndex >= 0 && slotIndex < displaySlots && slotUuids.containsKey(slotIndex)) {
            if (player instanceof ServerPlayer serverPlayer) {
                String uuid = slotUuids.get(slotIndex);
                String command = button == 0
                    ? "/status set " + uuid + " "
                    : "/status color " + uuid + " ";
                serverPlayer.closeContainer();
                serverPlayer.sendSystemMessage(Component.literal("\u00a7eTippe: \u00a7f" + command), false);
                return;
            }
        }
        super.clicked(slotIndex, button, clickType, player);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    public static HeadContainer createContainer(int size) {
        return new HeadContainer(size);
    }

    public static class HeadContainer extends SimpleContainer {
        public final Map<Integer, String> slotUuids = new HashMap<>();

        public HeadContainer(int size) {
            super(size);
        }

        public void setSlotUuid(int slot, String uuid) {
            slotUuids.put(slot, uuid);
        }
    }
}
