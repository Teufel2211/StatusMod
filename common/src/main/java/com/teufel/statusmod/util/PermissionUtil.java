package com.teufel.statusmod.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teufel.statusmod.StatusMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PermissionUtil {
    private static boolean luckypermsAvailable = false;
    private static Object luckypermsApi = null;
    private static final long OPS_FILE_CACHE_MS = 10_000L;
    private static volatile long lastOpsFileReadAt = 0L;
    private static volatile Set<String> cachedOpsNames = new HashSet<>();
    private static volatile Map<String, Integer> cachedOpsLevels = new ConcurrentHashMap<>();

    static {
        try {
            Class.forName("net.luckperms.api.LuckPermsProvider");
            luckypermsAvailable = true;
            try {
                Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
                java.lang.reflect.Method getMethod = providerClass.getMethod("get");
                luckypermsApi = getMethod.invoke(null);
            } catch (Exception e) {
                luckypermsAvailable = false;
            }
        } catch (ClassNotFoundException e) {
            System.out.println("[StatusMod] LuckyPerms not detected - using operator fallback");
        }
    }

    public static boolean hasStatusPermission(CommandSourceStack src) {
        ServerPlayer player = null;
        try { player = src.getPlayer(); } catch (Exception ignored) {}
        boolean op = hasOperatorPermission(src);
        if (player != null && luckypermsAvailable && luckypermsApi != null) {
            return checkLuckyPermsPermission(player, StatusMod.getConfig().statusPermissionNode) || op;
        }
        return op;
    }

    public static boolean hasAdminPermission(CommandSourceStack src) {
        ServerPlayer player = null;
        try { player = src.getPlayer(); } catch (Exception ignored) {}
        boolean op = hasOperatorPermission(src);
        if (player != null && luckypermsAvailable && luckypermsApi != null) {
            return checkLuckyPermsPermission(player, StatusMod.getConfig().adminPermissionNode) || op;
        }
        return op;
    }

    public static boolean hasAdminPermission(ServerPlayer player) {
        if (player == null) return false;
        try {
            if (luckypermsAvailable && luckypermsApi != null) {
                if (checkLuckyPermsPermission(player, StatusMod.getConfig().adminPermissionNode)) return true;
            }
        } catch (Exception ignored) {}
        int requiredLevel = Math.max(1, StatusMod.getConfig() != null ? StatusMod.getConfig().adminOpLevel : 2);
        return hasPlayerPermissionLevel(player, requiredLevel) || isOpByOpsFileFallback(player, requiredLevel);
    }

    private static boolean checkLuckyPermsPermission(ServerPlayer player, String permission) {
        try {
            if (luckypermsApi == null) return false;
            Class<?> apiClass = luckypermsApi.getClass();
            java.lang.reflect.Method getPlayerAdapterMethod = apiClass.getMethod("getPlayerAdapter", Class.class);
            Object playerAdapter = getPlayerAdapterMethod.invoke(luckypermsApi, ServerPlayer.class);
            java.lang.reflect.Method getUserMethod = playerAdapter.getClass().getMethod("getUser", ServerPlayer.class);
            Object user = getUserMethod.invoke(playerAdapter, player);
            if (user == null) return false;
            java.lang.reflect.Method getCachedDataMethod = user.getClass().getMethod("getCachedData");
            Object cachedData = getCachedDataMethod.invoke(user);
            java.lang.reflect.Method getPermissionDataMethod = cachedData.getClass().getMethod("getPermissionData");
            Object permissionData = getPermissionDataMethod.invoke(cachedData);
            java.lang.reflect.Method checkPermissionMethod = permissionData.getClass().getMethod("checkPermission", String.class);
            Object result = checkPermissionMethod.invoke(permissionData, permission);
            java.lang.reflect.Method asBooleanMethod = result.getClass().getMethod("asBoolean");
            return (boolean) asBooleanMethod.invoke(result);
        } catch (Exception e) {
            System.err.println("[StatusMod] LuckyPerms permission check failed: " + e.getMessage());
            return false;
        }
    }

    private static boolean isConsoleSource(CommandSourceStack src) {
        if (src == null) return false;
        try {
            Object entity = null;
            try { entity = src.getEntity(); } catch (Exception ignored) {}
            if (entity != null) return false;
            Object server = null;
            try { server = src.getServer(); } catch (Exception ignored) {}
            if (server == null) return false;
            try {
                java.lang.reflect.Method getLevel = src.getClass().getMethod("getLevel");
                Object level = getLevel.invoke(src);
                if (level != null) return false;
            } catch (Exception ignored) {}
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean hasOperatorPermission(CommandSourceStack src) {
        int requiredLevel = 2;
        try {
            if (StatusMod.getConfig() != null) requiredLevel = Math.max(0, StatusMod.getConfig().adminOpLevel);
        } catch (Exception ignored) {}
        if (isConsoleSource(src)) return true;
        if (hasSourcePermissionLevel(src, requiredLevel)) return true;
        if (requiredLevel > 2 && hasSourcePermissionLevel(src, 2)) return true;
        ServerPlayer directPlayer = null;
        try { directPlayer = src.getPlayer(); } catch (Exception ignored) {}
        if (directPlayer != null) {
            if (hasPlayerPermissionLevel(directPlayer, requiredLevel)) return true;
            if (requiredLevel > 2 && hasPlayerPermissionLevel(directPlayer, 2)) return true;
            if (isOpByOpsFileFallback(directPlayer)) return true;
        }
        return false;
    }

    public static boolean isLuckyPermsAvailable() {
        return luckypermsAvailable && luckypermsApi != null;
    }

    private static boolean isOpByOpsFileFallback(ServerPlayer player) {
        return isOpByOpsFileFallback(player, 0);
    }

    private static boolean isOpByOpsFileFallback(ServerPlayer player, int minLevel) {
        try {
            if (player == null) return false;
            String currentName = player.getScoreboardName();
            if (currentName == null || currentName.isBlank()) return false;
            long now = System.currentTimeMillis();
            if ((now - lastOpsFileReadAt) > OPS_FILE_CACHE_MS) {
                try {
                    java.lang.reflect.Method m = player.getClass().getMethod("getServer");
                    net.minecraft.server.MinecraftServer server = (net.minecraft.server.MinecraftServer) m.invoke(player);
                    if (server != null) {
                        reloadOpsFileCache(server);
                        lastOpsFileReadAt = now;
                    }
                } catch (Exception ignored) {}
            }
            String key = currentName.toLowerCase();
            if (!cachedOpsNames.contains(key)) return false;
            if (minLevel <= 0) return true;
            Integer level = cachedOpsLevels.get(key);
            return level != null && level >= minLevel;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void reloadOpsFileCache(MinecraftServer server) {
        Set<String> names = new HashSet<>();
        Map<String, Integer> levels = new ConcurrentHashMap<>();
        try {
            Path opsPath = server.getServerDirectory().resolve("ops.json");
            if (!Files.exists(opsPath)) {
                cachedOpsNames = names;
                cachedOpsLevels = levels;
                return;
            }
            String json = Files.readString(opsPath);
            JsonElement root = JsonParser.parseString(json);
            if (!(root instanceof JsonArray arr)) {
                cachedOpsNames = names;
                cachedOpsLevels = levels;
                return;
            }
            for (JsonElement e : arr) {
                if (!(e instanceof JsonObject obj)) continue;
                JsonElement nameEl = obj.get("name");
                if (nameEl == null || nameEl.isJsonNull()) continue;
                JsonElement levelEl = obj.get("level");
                int level = (levelEl != null && !levelEl.isJsonNull()) ? levelEl.getAsInt() : 0;
                String n = nameEl.getAsString();
                if (n == null || n.isBlank()) continue;
                String key = n.toLowerCase();
                names.add(key);
                levels.put(key, level);
            }
        } catch (Exception ignored) {}
        cachedOpsNames = names;
        cachedOpsLevels = levels;
    }

    private static boolean hasSourcePermissionLevel(CommandSourceStack src, int level) {
        if (src == null) return false;
        try {
            java.lang.reflect.Method m = src.getClass().getMethod("hasPermission", int.class);
            Object r = m.invoke(src, level);
            return (r instanceof Boolean b) && b;
        } catch (Exception ignored) {}
        try {
            java.lang.reflect.Method m = src.getClass().getMethod("hasPermissionLevel", int.class);
            Object r = m.invoke(src, level);
            return (r instanceof Boolean b) && b;
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean hasPlayerPermissionLevel(ServerPlayer player, int level) {
        if (player == null) return false;
        String[] candidates = new String[]{"hasPermissions", "hasPermissionLevel", "hasPermission"};
        for (String name : candidates) {
            try {
                java.lang.reflect.Method m = player.getClass().getMethod(name, int.class);
                Object r = m.invoke(player, level);
                if (r instanceof Boolean b && b) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }
}
