package com.teufel.statusmod.gui;

import com.teufel.statusmod.command.StatusCommand;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Beginner-friendly self-status menu (3 rows). Top two rows: clickable presets
 * (built-in + own customs). Bottom row: current-status display + exactly two
 * buttons: next preset (applies immediately) and clear status.
 * Same click mechanics as ClickablePlayerHeadMenu (slotChanged + restore),
 * so it works on 1.21.11 and 26.x (and Bedrock via Geyser inventories).
 */
public class SelfStatusMenu extends AbstractContainerMenu {
    private static final long ACTION_DEBOUNCE_MS = 400L;
    public static final int SIZE = 27;
    public static final int PRESET_SLOTS = 18;
    public static final int SLOT_DISPLAY = 19;
    public static final int SLOT_NEXT = 21;
    public static final int SLOT_CLEAR = 23;

    public static final class Entry {
        public final String key;
        public final String label;
        public final String color;
        public Entry(String key, String label, String color) {
            this.key = key;
            this.label = label == null ? "" : label;
            this.color = color == null ? "reset" : color;
        }
    }

    /**
     * Maps our color keys to wool variants. 1.21.11 has Items.&lt;X&gt;_WOOL fields,
     * 26.x consolidated them into Items.WOOL (ColorCollection record with named
     * accessors). Both resolved reflectively; PAPER fallback.
     * {colorKey, fieldSuffix (1.21.11), accessor (26.x)}
     */
    private static final String[][] WOOL_VARIANTS = {
        {"black", "BLACK", "black"},
        {"dark_blue", "BLUE", "blue"},
        {"dark_green", "GREEN", "green"},
        {"dark_aqua", "CYAN", "cyan"},
        {"dark_red", "RED", "red"},
        {"dark_purple", "PURPLE", "purple"},
        {"gold", "ORANGE", "orange"},
        {"gray", "LIGHT_GRAY", "lightGray"},
        {"dark_gray", "GRAY", "gray"},
        {"blue", "BLUE", "blue"},
        {"green", "LIME", "lime"},
        {"aqua", "LIGHT_BLUE", "lightBlue"},
        {"red", "RED", "red"},
        {"light_purple", "MAGENTA", "magenta"},
        {"yellow", "YELLOW", "yellow"},
        {"white", "WHITE", "white"},
    };

    private static Item woolFor(String colorKey) {
        String key = colorKey == null ? "white" : colorKey.toLowerCase();
        String suffix = "WHITE";
        String accessor = "white";
        for (String[] v : WOOL_VARIANTS) {
            if (v[0].equals(key)) {
                suffix = v[1];
                accessor = v[2];
                break;
            }
        }
        try {
            Object o = Items.class.getField(suffix + "_WOOL").get(null);
            if (o instanceof Item item) return item;
        } catch (Throwable ignored) {}
        try {
            Object wool = Items.class.getField("WOOL").get(null);
            if (wool != null) {
                Method m = wool.getClass().getMethod(accessor);
                Object o = m.invoke(wool);
                if (o instanceof Item item) return item;
            }
        } catch (Throwable ignored) {}
        return Items.PAPER;
    }

    private final Player viewer;
    private final CommandSourceStack cmdSource;
    private final List<Entry> entries;
    private final SimpleContainer container;
    private final ItemStack[] saved;
    private long lastActionAtMs;

    public SelfStatusMenu(int syncId, Inventory playerInventory, Player viewer,
                          CommandSourceStack cmdSource, List<Entry> entries) {
        super(MenuType.GENERIC_9x3, syncId);
        this.viewer = viewer;
        this.cmdSource = cmdSource;
        this.entries = entries == null ? new ArrayList<>() : entries;
        this.container = new SimpleContainer(SIZE);
        this.saved = new ItemStack[SIZE];
        for (int i = 0; i < SIZE; i++) saved[i] = ItemStack.EMPTY;

        fillContainer();

        for (int i = 0; i < SIZE; i++) {
            final int idx = i;
            addSlot(new Slot(container, idx, 8 + (idx % 9) * 18, 18 + (idx / 9) * 18) {
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

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        final boolean[] wasPopulated = new boolean[SIZE];
        final boolean[] initialized = {false};
        addSlotListener(new ContainerListener() {
            @Override
            public void slotChanged(AbstractContainerMenu menu, int slotIndex, ItemStack stack) {
                if (!initialized[0]) {
                    if (slotIndex >= 0 && slotIndex < SIZE && !stack.isEmpty()) {
                        wasPopulated[slotIndex] = true;
                    }
                    if (slotIndex >= SIZE) {
                        initialized[0] = true;
                    }
                    return;
                }
                if (slotIndex < 0 || slotIndex >= SIZE) return;
                if (!wasPopulated[slotIndex]) return;

                ItemStack savedStack = saved[slotIndex];
                boolean emptied = stack.isEmpty();
                boolean polluted = !emptied && !savedStack.isEmpty() && stack.getItem() != savedStack.getItem();
                if (!emptied && !polluted) return;
                wasPopulated[slotIndex] = false;

                container.setItem(slotIndex, savedStack.copy());
                try {
                    menu.setCarried(polluted ? stack.copy() : ItemStack.EMPTY);
                } catch (Throwable ignored) {}
                if (polluted) return;
                handleSlotAction(slotIndex);
            }

            @Override
            public void dataChanged(AbstractContainerMenu menu, int dataId, int value) {}
        });
    }

    private void fillContainer() {
        for (int i = 0; i < Math.min(entries.size(), PRESET_SLOTS); i++) {
            Entry e = entries.get(i);
            Item wool = woolFor(e.color);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("Klicken zum Setzen").withStyle(ChatFormatting.GRAY));
            container.setItem(i, makeItem(wool, e.label, ChatFormatting.WHITE, lore));
        }
        container.setItem(SLOT_DISPLAY, buildDisplayItem());
        fillGlass(SLOT_DISPLAY);
        container.setItem(SLOT_NEXT, makeItem(Items.ARROW, "\u25B6 Weiter",
            ChatFormatting.GREEN, hintLore("N\u00E4chstes Preset setzen")));
        container.setItem(SLOT_CLEAR, makeItem(Items.BARRIER, "\u2716 Status l\u00F6schen",
            ChatFormatting.RED, hintLore("Status entfernen")));
        for (int i = 0; i < SIZE; i++) {
            saved[i] = container.getItem(i).copy();
        }
    }

    private void fillGlass(int except1) {
        for (int i = PRESET_SLOTS; i < SIZE; i++) {
            if (i == except1 || i == SLOT_DISPLAY || i == SLOT_NEXT || i == SLOT_CLEAR) continue;
            if (container.getItem(i).isEmpty()) {
                // Plain GLASS_PANE exists on all versions (colored variants were
                // consolidated into ColorCollections on 26.x).
                container.setItem(i, makeItem(Items.GLASS_PANE, " ", ChatFormatting.GRAY, new ArrayList<>()));
            }
        }
        for (int i = 0; i < SIZE; i++) {
            if (saved[i].isEmpty()) saved[i] = container.getItem(i).copy();
        }
    }

    private static List<Component> hintLore(String text) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal(text).withStyle(ChatFormatting.GRAY));
        return lore;
    }

    private ItemStack buildDisplayItem() {
        String current = StatusCommand.getOwnStatusText(cmdSource);
        List<Component> lore = new ArrayList<>();
        if (current == null || current.isEmpty()) {
            lore.add(Component.literal("Aktuell: kein Status").withStyle(ChatFormatting.GRAY));
        } else {
            MutableComponent line = Component.literal(current);
            TextColor c = StatusCommand.getOwnStatusColor(cmdSource);
            line.withStyle(Style.EMPTY.withColor(c != null ? c : TextColor.fromRgb(0xFFFFFF)));
            lore.add(line);
        }
        lore.add(Component.literal("Wird nach jeder Aktion aktualisiert.").withStyle(ChatFormatting.DARK_GRAY));
        return makeItem(Items.PAPER, "Dein Status", ChatFormatting.YELLOW, lore);
    }

    private void refreshDisplay() {
        try {
            ItemStack display = buildDisplayItem();
            saved[SLOT_DISPLAY] = display.copy();
            container.setItem(SLOT_DISPLAY, display);
        } catch (Throwable e) {
            System.err.println("[StatusMod] GUI refresh failed: " + e.getMessage());
        }
    }

    private void handleSlotAction(int slotIndex) {
        try {
            long now = System.currentTimeMillis();
            if (now - lastActionAtMs < ACTION_DEBOUNCE_MS) return;
            lastActionAtMs = now;

            if (slotIndex >= 0 && slotIndex < Math.min(entries.size(), PRESET_SLOTS)) {
                StatusCommand.guiApplyPreset(cmdSource, entries.get(slotIndex).key);
            } else if (slotIndex == SLOT_NEXT) {
                StatusCommand.guiCyclePreset(cmdSource);
            } else if (slotIndex == SLOT_CLEAR) {
                StatusCommand.guiClearStatus(cmdSource);
            } else {
                return;
            }
            refreshDisplay();
        } catch (Throwable e) {
            System.err.println("[StatusMod] GUI action failed: " + e.getMessage());
        }
    }

    private static ItemStack makeItem(Item item, String name, ChatFormatting nameColor, List<Component> lore) {
        ItemStack stack = new ItemStack(item);
        try {
            Class<?> dcClass = Class.forName("net.minecraft.core.component.DataComponents");
            Class<?> dctClass = Class.forName("net.minecraft.core.component.DataComponentType");
            Method setMethod = null;
            for (Method m : stack.getClass().getMethods()) {
                if (!m.getName().equals("set") || m.getParameterCount() != 2) continue;
                if (m.getParameterTypes()[0].isAssignableFrom(dctClass)
                    && m.getParameterTypes()[1].isAssignableFrom(Object.class)) {
                    setMethod = m;
                    break;
                }
            }
            if (setMethod == null) return stack;
            Object customNameKey = dcClass.getField("CUSTOM_NAME").get(null);
            setMethod.invoke(stack, customNameKey, Component.literal(name).withStyle(nameColor));
            if (lore != null && !lore.isEmpty()) {
                Object loreKey = dcClass.getField("LORE").get(null);
                Class<?> loreClass = Class.forName("net.minecraft.world.item.component.ItemLore");
                Object loreObj = loreClass.getConstructor(List.class).newInstance(new ArrayList<>(lore));
                setMethod.invoke(stack, loreKey, loreObj);
            }
        } catch (Throwable ignored) {}
        return stack;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        try {
            if (index >= 0 && index < SIZE) {
                long now = System.currentTimeMillis();
                if (now - lastActionAtMs < ACTION_DEBOUNCE_MS) return ItemStack.EMPTY;
                lastActionAtMs = now;
                if (index < Math.min(entries.size(), PRESET_SLOTS)) {
                    StatusCommand.guiApplyPreset(cmdSource, entries.get(index).key);
                } else if (index == SLOT_NEXT) {
                    StatusCommand.guiCyclePreset(cmdSource);
                } else if (index == SLOT_CLEAR) {
                    StatusCommand.guiClearStatus(cmdSource);
                } else {
                    return ItemStack.EMPTY;
                }
                refreshDisplay();
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
