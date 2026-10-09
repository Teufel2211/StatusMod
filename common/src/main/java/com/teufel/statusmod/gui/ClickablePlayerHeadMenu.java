package com.teufel.statusmod.gui;

import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.storage.PlayerSettings;
import com.teufel.statusmod.util.PermissionUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;

public class ClickablePlayerHeadMenu extends AbstractContainerMenu {
    private static final long ACTION_DEBOUNCE_MS = 400L;
    private final Map<Integer, String> slotUuids;
    private final Player viewer;
    private final CommandSourceStack cmdSource;
    private final ItemStack[] savedHeads;
    private long lastActionAtMs;

    public ClickablePlayerHeadMenu(int syncId, Inventory playerInventory, PlayerHeadMenu.HeadContainer container, Player viewer, CommandSourceStack cmdSource) {
        super(MenuType.GENERIC_9x6, syncId);
        this.slotUuids = container.slotUuids;
        this.viewer = viewer;
        this.cmdSource = cmdSource;
        this.savedHeads = new ItemStack[54];

        container.startOpen(playerInventory.player);

        for (int i = 0; i < 54; i++) {
            ItemStack head = container.getItem(i).copy();
            savedHeads[i] = head;
            container.setItem(i, head.copy());
        }

        final boolean[] wasPopulated = new boolean[54];
        final boolean[] initialized = {false};

        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                int idx = row * 9 + col;
                addSlot(new Slot(container, idx, 8 + col * 18, 18 + row * 18) {
                    @Override
                    public boolean mayPickup(Player player) {
                        return true;
                    }

                    @Override
                    public boolean mayPlace(ItemStack stack) {
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

        addSlotListener(new ContainerListener() {
            @Override
            public void slotChanged(AbstractContainerMenu menu, int slotIndex, ItemStack stack) {
                if (!initialized[0]) {
                    if (slotIndex >= 0 && slotIndex < 54 && !stack.isEmpty()) {
                        wasPopulated[slotIndex] = true;
                    }
                    if (slotIndex >= 54) {
                        initialized[0] = true;
                    }
                    return;
                }
                if (slotIndex < 0 || slotIndex >= 54) return;
                if (!wasPopulated[slotIndex]) return;

                ItemStack saved = savedHeads[slotIndex];
                boolean emptied = stack.isEmpty();
                boolean polluted = !emptied && !saved.isEmpty() && stack.getItem() != saved.getItem();

                if (!emptied && !polluted) return;
                wasPopulated[slotIndex] = false;

                container.setItem(slotIndex, saved.copy());
                try {
                    menu.setCarried(polluted ? stack.copy() : ItemStack.EMPTY);
                } catch (Throwable ignored) {}

                if (polluted) return;
                handleHeadAction(slotIndex, false);
            }

            @Override
            public void dataChanged(AbstractContainerMenu menu, int dataId, int value) {}
        });
    }

    private static void tryCloseContainer(Player viewer) {
        // Direct call: ServerPlayer.closeContainer() is public on 1.21.11 and
        // 26.x (Player.closeContainer is protected on 26.x, so no direct call
        // on the Player type). Old name reflection failed on intermediary
        // runtimes (Fabric/Quilt 1.21.11).
        try {
            if (viewer instanceof ServerPlayer sp) {
                sp.closeContainer();
                return;
            }
        } catch (Throwable ignored) {}
        try {
            java.lang.reflect.Method m = Player.class.getDeclaredMethod("closeContainer");
            m.setAccessible(true);
            m.invoke(viewer);
            return;
        } catch (Throwable ignored) {}
        try {
            java.lang.reflect.Field cm = Player.class.getField("containerMenu");
            java.lang.reflect.Field im = Player.class.getField("inventoryMenu");
            cm.set(viewer, im.get(viewer));
        } catch (Throwable e) {
            System.err.println("[StatusMod] Could not close GUI: " + e.getMessage());
        }
    }

    private String resolveTargetName(String uuid) {
        try {
            ServerPlayer online = cmdSource.getServer().getPlayerList().getPlayer(UUID.fromString(uuid));
            if (online != null) return online.getScoreboardName();
        } catch (Throwable ignored) {}
        try {
            PlayerSettings ps = StatusMod.getStorage().forPlayer(uuid);
            if (ps != null && ps.lastKnownName != null && !ps.lastKnownName.isEmpty()) return ps.lastKnownName;
        } catch (Throwable ignored) {}
        return uuid.length() > 8 ? uuid.substring(0, 8) : uuid;
    }

    private static MutableComponent clickable(MutableComponent text, String command, String hoverText) {
        // Direct record construction (verified identical Mojang signatures on
        // 1.21.11 and 26.x). Old 3-stage name reflection never reached the
        // record branch on intermediary runtimes (Fabric/Quilt 1.21.11).
        try {
            text = text.withStyle(Style.EMPTY.withClickEvent(new ClickEvent.SuggestCommand(command)));
        } catch (Throwable ignored) {}
        try {
            text = text.withStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(Component.literal(hoverText))));
        } catch (Throwable ignored) {}
        return text;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    private void handleHeadAction(int slotIndex, boolean colorMode) {
        try {
            long now = System.currentTimeMillis();
            if (now - lastActionAtMs < ACTION_DEBOUNCE_MS) return;
            lastActionAtMs = now;

            String targetUuid = slotUuids.get(slotIndex);
            if (targetUuid == null || !(viewer instanceof ServerPlayer admin)) return;
            if (!PermissionUtil.hasAdminPermission(admin)) return;

            String targetName = resolveTargetName(targetUuid);
            String command = colorMode
                ? "/status admin color " + targetName + " "
                : "/status admin set " + targetName + " ";

            tryCloseContainer(viewer);

            String label = colorMode
                ? "\u25B6 Farbe von " + targetName + " \u00E4ndern \u2013 Klicken bef\u00FCllt den Chat"
                : "\u25B6 Status von " + targetName + " \u00E4ndern \u2013 Klicken bef\u00FCllt den Chat";
            MutableComponent line = Component.literal(label).withStyle(colorMode ? net.minecraft.ChatFormatting.AQUA : net.minecraft.ChatFormatting.YELLOW);
            MutableComponent hint = Component.literal("  " + command.trim() + " <" + (colorMode ? "Farbe" : "Status") + ">").withStyle(net.minecraft.ChatFormatting.GRAY);
            admin.sendSystemMessage(clickable(line.append(hint), command, "Klicken zum Einf\u00FCgen in den Chat"));
        } catch (Throwable e) {
            System.err.println("[StatusMod] GUI action failed: " + e.getMessage());
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        try {
            if (index >= 0 && index < 54 && slotUuids.containsKey(index)) {
                handleHeadAction(index, true);
            }
        } catch (Throwable e) {
            System.err.println("[StatusMod] GUI shift-click failed: " + e.getMessage());
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
    }
}
