package com.teufel.statusmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.gui.PlayerHeadMenu;
import com.teufel.statusmod.storage.PlayerSettings;
import com.teufel.statusmod.util.PermissionUtil;
import com.teufel.statusmod.util.StatusTextUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.mojang.authlib.GameProfile;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StatusGuiCommand {
    private static final int MAX_HEADS = 54;
    private static Boolean hasDataComponents = null;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("status")
            .then(Commands.literal("gui").executes(ctx -> {
                if (!PermissionUtil.hasAdminPermission(ctx.getSource())) {
                    ctx.getSource().sendFailure(Component.literal("Du hast nicht genugend Rechte."));
                    return 0;
                }
                ServerPlayer player = ctx.getSource().getPlayer();
                if (player == null) {
                    ctx.getSource().sendFailure(Component.literal("Nur Spieler können diesen Befehl nutzen."));
                    return 0;
                }
                openStatusGui(ctx.getSource(), player);
                return 1;
            }))
        );
    }

    private static void openStatusGui(CommandSourceStack source, ServerPlayer player) {
        MinecraftServer server = getServer(source);
        if (server == null) {
            source.sendFailure(Component.literal("Server nicht gefunden."));
            return;
        }

        Container container = PlayerHeadMenu.createContainer(MAX_HEADS);

        Map<String, ServerPlayer> onlineMap = new HashMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            onlineMap.put(p.getUUID().toString(), p);
        }

        int slot = 0;
        Map<String, PlayerSettings> snapshot = StatusMod.getStorage().getAllSnapshot();

        for (Map.Entry<String, PlayerSettings> entry : snapshot.entrySet()) {
            if (slot >= MAX_HEADS) break;
            String uuid = entry.getKey();
            PlayerSettings ps = entry.getValue();
            ServerPlayer online = onlineMap.get(uuid);
            String name;
            if (online != null) {
                name = online.getScoreboardName();
            } else if (ps.lastKnownName != null && !ps.lastKnownName.isEmpty()) {
                name = ps.lastKnownName;
            } else {
                name = uuid.substring(0, 8);
            }
            container.setItem(slot, createPlayerHead(name, ps, online, uuid));
            slot++;
        }

        for (Map.Entry<String, ServerPlayer> entry : onlineMap.entrySet()) {
            if (slot >= MAX_HEADS) break;
            String uuid = entry.getKey();
            if (snapshot.containsKey(uuid)) continue;
            ServerPlayer online = entry.getValue();
            PlayerSettings ps = StatusMod.getStorage().forPlayer(uuid);
            container.setItem(slot, createPlayerHead(online.getScoreboardName(), ps, online, uuid));
            slot++;
        }

        MenuProvider provider = new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Status Players");
            }

            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory playerInv, Player p) {
                return new PlayerHeadMenu(syncId, playerInv, container);
            }
        };

        player.openMenu(provider);
    }

    private static ItemStack createPlayerHead(String name, PlayerSettings settings, ServerPlayer onlinePlayer, String uuidStr) {
        if (hasDataComponents == null) {
            try {
                Class.forName("net.minecraft.core.component.DataComponents");
                hasDataComponents = true;
            } catch (ClassNotFoundException e) {
                hasDataComponents = false;
            }
        }

        boolean isOnline = onlinePlayer != null;
        UUID uuid = null;
        try {
            uuid = java.util.UUID.fromString(uuidStr);
        } catch (Exception ignored) {}

        GameProfile gameProfile = null;
        if (isOnline) {
            try {
                gameProfile = onlinePlayer.getGameProfile();
            } catch (Throwable ignored) {}
        }
        if (gameProfile == null && uuid != null) {
            gameProfile = new GameProfile(uuid, name);
        }

        if (hasDataComponents) {
            try {
                return createPlayerHeadModern(name, settings, isOnline, gameProfile);
            } catch (Throwable t) {
                hasDataComponents = false;
            }
        }

        return createPlayerHeadLegacy(name, settings, isOnline, gameProfile);
    }

    @SuppressWarnings("unchecked")
    private static ItemStack createPlayerHeadModern(String name, PlayerSettings settings, boolean online, GameProfile profile) throws Throwable {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        Class<?> dcClass = Class.forName("net.minecraft.core.component.DataComponents");
        Class<? extends Enum<?>> keyEnum = (Class<? extends Enum<?>>) dcClass.getField("CUSTOM_NAME").get(null).getClass();
        Method setMethod = findMethod(stack.getClass(), "set", keyEnum, Object.class);

        // Set skull profile for real player skin
        if (profile != null) {
            Object profileKey = dcClass.getField("PROFILE").get(null);
            Enum<?> profileConstant = Enum.valueOf((Class) keyEnum, "PROFILE");
            setMethod.invoke(stack, profileConstant, profile);
        }

        String statusText = settings != null ? settings.status : "";
        String colorKey = settings != null ? settings.color : "reset";

        Enum<?> keyConstant = Enum.valueOf((Class) keyEnum, "CUSTOM_NAME");
        MutableComponent displayName = Component.literal(name).withStyle(ChatFormatting.WHITE);
        setMethod.invoke(stack, keyConstant, displayName);

        // Lore
        List<Component> loreLines = new ArrayList<>();

        if (statusText != null && !statusText.isEmpty()) {
            String bracketed = StatusTextUtil.wrapBrackets(statusText, settings != null ? settings.brackets : 0);
            TextColor statusColor = resolveColor(colorKey);
            MutableComponent statusLine = Component.literal(bracketed);
            if (statusColor != null) {
                statusLine.withStyle(Style.EMPTY.withColor(statusColor));
            } else {
                statusLine.withStyle(ChatFormatting.RESET);
            }
            loreLines.add(statusLine);
        } else {
            loreLines.add(Component.literal("- Kein Status -").withStyle(ChatFormatting.GRAY));
        }

        loreLines.add(Component.literal(online ? "\u00a7aOnline" : "\u00a77Offline"));

        Class<?> loreClass = Class.forName("net.minecraft.world.item.component.ItemLore");
        Object lore = loreClass.getConstructor(List.class).newInstance(loreLines);

        Enum<?> loreConstant = Enum.valueOf((Class) keyEnum, "LORE");
        setMethod.invoke(stack, loreConstant, lore);

        return stack;
    }

    private static ItemStack createPlayerHeadLegacy(String name, PlayerSettings settings, boolean online, GameProfile profile) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);

        // Set skull owner for real player skin
        if (profile != null) {
            try {
                Object id = findMethod(profile.getClass(), "getId").invoke(profile);
                if (id != null) {
                    CompoundTag skullOwner = new CompoundTag();
                    skullOwner.putString("Id", id.toString());
                    Object profileName = findMethod(profile.getClass(), "getName").invoke(profile);
                    if (profileName != null) {
                        skullOwner.putString("Name", profileName.toString());
                    }
                    getOrCreateTag(stack).put("SkullOwner", skullOwner);
                }
            } catch (Throwable ignored) {}
        }

        String statusText = settings != null ? settings.status : "";
        String colorKey = settings != null ? settings.color : "reset";

        // Set display name via NBT
        MutableComponent displayName = Component.literal(name).withStyle(ChatFormatting.WHITE);
        String nameJson = componentToJson(displayName);
        if (nameJson != null) {
            CompoundTag display = new CompoundTag();
            display.putString("Name", nameJson);
            getOrCreateTag(stack).put("display", display);
        }

        // Lore via NBT
        ListTag lore = new ListTag();

        if (statusText != null && !statusText.isEmpty()) {
            String bracketed = StatusTextUtil.wrapBrackets(statusText, settings != null ? settings.brackets : 0);
            TextColor statusColor = resolveColor(colorKey);
            MutableComponent statusLine = Component.literal(bracketed);
            if (statusColor != null) {
                statusLine.withStyle(Style.EMPTY.withColor(statusColor));
            } else {
                statusLine.withStyle(ChatFormatting.RESET);
            }
            String json = componentToJson(statusLine);
            if (json != null) lore.add(StringTag.valueOf(json));
        } else {
            MutableComponent noStatus = Component.literal("- Kein Status -");
            noStatus.withStyle(ChatFormatting.GRAY);
            String json = componentToJson(noStatus);
            if (json != null) lore.add(StringTag.valueOf(json));
        }

        String stateText = online ? "\u00a7aOnline" : "\u00a77Offline";
        MutableComponent stateLine = Component.literal(stateText);
        String stateJson = componentToJson(stateLine);
        if (stateJson != null) lore.add(StringTag.valueOf(stateJson));

        CompoundTag tag = getOrCreateTag(stack);
        CompoundTag displayTag = getCompoundTag(tag, "display");
        displayTag.put("Lore", lore);
        tag.put("display", displayTag);

        return stack;
    }

    private static CompoundTag getOrCreateTag(ItemStack stack) {
        try {
            Method m = findMethod(stack.getClass(), "getOrCreateTag");
            return (CompoundTag) m.invoke(stack);
        } catch (Throwable e) {
            return new CompoundTag();
        }
    }

    private static CompoundTag getCompoundTag(CompoundTag tag, String key) {
        try {
            Object result = tag.getCompound(key);
            if (result instanceof CompoundTag ct) return ct;
            if (result instanceof Optional<?> opt && opt.isPresent()) return (CompoundTag) opt.get();
        } catch (Throwable ignored) {}
        return new CompoundTag();
    }

    private static String componentToJson(Component component) {
        try {
            Class<?> serializerClass = Class.forName("net.minecraft.network.chat.Component$Serializer");
            Method toJson = serializerClass.getMethod("toJson", Component.class);
            Object result = toJson.invoke(null, component);
            return result instanceof String ? (String) result : null;
        } catch (Throwable e) {
            return component.getString();
        }
    }

    private static MinecraftServer getServer(CommandSourceStack src) {
        try {
            Method m = findMethod(src.getClass(), "getServer");
            return m != null ? (MinecraftServer) m.invoke(src) : null;
        } catch (Throwable e) {
            return null;
        }
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        Class<?> c = clazz;
        while (c != null) {
            try {
                return c.getDeclaredMethod(name, paramTypes);
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static TextColor resolveColor(String colorKey) {
        if (colorKey == null || colorKey.isEmpty()) return null;

        if (colorKey.equalsIgnoreCase("rainbow") || colorKey.equalsIgnoreCase("animated")) {
            return TextColor.fromRgb(0xFF5555);
        }

        if (colorKey.startsWith("#")) {
            try {
                String hex = colorKey.substring(1);
                if (hex.length() == 3) {
                    char r = hex.charAt(0), g = hex.charAt(1), b = hex.charAt(2);
                    hex = "" + r + r + g + g + b + b;
                }
                return TextColor.fromRgb(Integer.parseInt(hex, 16));
            } catch (Exception ignored) {}
        }

        try {
            ChatFormatting formatting = ChatFormatting.valueOf(colorKey.trim().toUpperCase());
            java.lang.reflect.Method getColor = findMethod(formatting.getClass(), "getColor");
            if (getColor != null) {
                Object result = getColor.invoke(formatting);
                if (result instanceof Integer color && color >= 0) {
                    return TextColor.fromRgb(color);
                }
            }
        } catch (Exception ignored) {}

        return null;
    }
}
