package com.teufel.statusmod.util;

import net.minecraft.commands.CommandSourceStack;
import com.teufel.statusmod.StatusMod;
import net.minecraft.server.level.ServerPlayer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Permission utility that integrates with LuckyPerms if available.
 * Falls back to basic permission checking if LuckyPerms is not installed.
 * Uses reflection to avoid compile-time dependency on LuckyPerms.
 */
public class PermissionUtil {
    private static boolean luckypermsAvailable = false;
    private static Object luckypermsApi = null;
    private static final Map<String, Long> LAST_DENY_LOG_AT = new ConcurrentHashMap<>();
    private static final long DENY_LOG_COOLDOWN_MS = 30_000L;
    private static final long OPS_FILE_CACHE_MS = 10_000L;
    private static volatile long lastOpsFileReadAt = 0L;
    private static volatile Set<String> cachedOpsNames = new HashSet<>();

    static {
        try {
            Class.forName("net.luckperms.api.LuckPermsProvider");
            luckypermsAvailable = true;
            
            // Try to initialize LuckyPerms API via reflection
            try {
                Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
                java.lang.reflect.Method getMethod = providerClass.getMethod("get");
                luckypermsApi = getMethod.invoke(null);
            } catch (Exception e) {
                System.err.println("[StatusMod] LuckyPerms found but failed to initialize API");
                luckypermsAvailable = false;
            }
            
            if (luckypermsAvailable) {
                System.out.println("[StatusMod] LuckyPerms detected - using advanced permission system");
            }
        } catch (ClassNotFoundException e) {
            System.out.println("[StatusMod] LuckyPerms not detected - using operator fallback");
        }
    }

    /**
     * Checks if a player has permission to use the status mod.
     * Uses LuckyPerms if available, otherwise checks if player is operator.
     */
    public static boolean hasStatusPermission(CommandSourceStack src) {
        ServerPlayer player = null;
        try { player = src.getPlayer(); } catch (Exception ignored) {}
        boolean op = hasOperatorPermission(src);
        if (player != null && luckypermsAvailable && luckypermsApi != null) {
            // if LP says yes, allow; otherwise fall back to operator permission
            boolean lpAllowed = checkLuckyPermsPermission(player, StatusMod.config.statusPermissionNode);
            if (!lpAllowed && !op) {
                logDenied("status", player, "op=" + op + ", lp=false");
            }
            return lpAllowed || op;
        }
        if (!op) {
            if (player != null) logDenied("status", player, "no LP, op=" + op);
            else System.out.println("[StatusMod][perm] status denied for non-player source, op=" + op);
        }
        // if LP isn't available just use operator/fallback check
        return op;
    }

    /**
     * Checks if a player has admin permission (can block other players).
     * Uses LuckyPerms if available, otherwise checks if player is operator.
     */
    public static boolean hasAdminPermission(CommandSourceStack src) {
        ServerPlayer player = null;
        try { player = src.getPlayer(); } catch (Exception ignored) {}
        boolean op = hasOperatorPermission(src);
        if (player != null && luckypermsAvailable && luckypermsApi != null) {
            // allow either the LP node or operator status
            boolean lpAllowed = checkLuckyPermsPermission(player, StatusMod.config.adminPermissionNode);
            if (!lpAllowed && !op) {
                logDenied("admin", player, "op=" + op + ", lp=false");
            }
            return lpAllowed || op;
        }
        if (!op) {
            if (player != null) logDenied("admin", player, "no LP, op=" + op);
            else System.out.println("[StatusMod][perm] admin denied for non-player source, op=" + op);
        }
        // Fallback: if no LP present just use operator check
        return op;
    }

    public static boolean hasAdminPermission(ServerPlayer player) {
        if (player == null) return false;
        try {
            if (luckypermsAvailable && luckypermsApi != null) {
                boolean lpAllowed = checkLuckyPermsPermission(player, StatusMod.config.adminPermissionNode);
                if (lpAllowed) return true;
            }
        } catch (Exception ignored) {}
        return hasPlayerPermissionLevel(player, 2) || isOpByOpsFileFallback(player);
    }

    /**
     * Checks a specific LuckyPerms permission for a player using reflection.
     */
    private static boolean checkLuckyPermsPermission(ServerPlayer player, String permission) {
        try {
            if (luckypermsApi == null) return false;
            
            // Use reflection to call LuckyPerms API methods
            // api.getPlayerAdapter(ServerPlayer.class).getUser(player)
            Class<?> apiClass = luckypermsApi.getClass();
            java.lang.reflect.Method getPlayerAdapterMethod = apiClass.getMethod("getPlayerAdapter", Class.class);
            Object playerAdapter = getPlayerAdapterMethod.invoke(luckypermsApi, ServerPlayer.class);
            
            java.lang.reflect.Method getUserMethod = playerAdapter.getClass().getMethod("getUser", ServerPlayer.class);
            Object user = getUserMethod.invoke(playerAdapter, player);
            
            if (user == null) return false;
            
            // user.getCachedData().getPermissionData().checkPermission(permission).asBoolean()
            java.lang.reflect.Method getCachedDataMethod = user.getClass().getMethod("getCachedData");
            Object cachedData = getCachedDataMethod.invoke(user);
            
            java.lang.reflect.Method getPermissionDataMethod = cachedData.getClass().getMethod("getPermissionData");
            Object permissionData = getPermissionDataMethod.invoke(cachedData);
            
            java.lang.reflect.Method checkPermissionMethod = permissionData.getClass().getMethod("checkPermission", String.class);
            Object result = checkPermissionMethod.invoke(permissionData, permission);
            
            java.lang.reflect.Method asBooleanMethod = result.getClass().getMethod("asBoolean");
            return (boolean) asBooleanMethod.invoke(result);
            
        } catch (Exception e) {
            System.err.println("[StatusMod] Error checking LuckyPerms permission: " + permission);
            e.printStackTrace();
            // Fallback to operator check if there's an error
            return false;
        }
    }

    private static boolean hasOperatorPermission(CommandSourceStack src) {
        int requiredLevel = 2;
        try {
            if (StatusMod.config != null) {
                requiredLevel = Math.max(0, StatusMod.config.adminOpLevel);
            }
        } catch (Exception ignored) {}

        if (hasSourcePermissionLevel(src, requiredLevel)) {
            return true;
        }
        if (requiredLevel > 2 && hasSourcePermissionLevel(src, 2)) {
            return true;
        }
        
        // Direct vanilla OP list check: if player is OP at all, allow admin commands
        // even when their OP level is below configured adminOpLevel.
        ServerPlayer directPlayer = null;
        try { directPlayer = src.getPlayer(); } catch (Exception ignored) {}
        if (directPlayer != null) {
            // Direct player-level permission probe. Some environments expose this
            // reliably even when CommandSourceStack hasPermission(...) is inconsistent.
            if (hasPlayerPermissionLevel(directPlayer, requiredLevel)) {
                return true;
            }
            if (requiredLevel > 2 && hasPlayerPermissionLevel(directPlayer, 2)) {
                return true;
            }
            if (isPlayerListedOp(src, directPlayer)) {
                return true;
            }
            // Alternate OP-list probe for environments where isOp(...) is unreliable.
            try {
                Object playerList = src.getServer() == null ? null : src.getServer().getPlayerList();
                if (playerList != null) {
                    java.lang.reflect.Method getOps = playerList.getClass().getMethod("getOps");
                    Object opsList = getOps.invoke(playerList);
                    if (opsList != null) {
                        Object profile = getPlayerProfileObject(directPlayer);
                        if (profile != null) {
                            for (java.lang.reflect.Method m : opsList.getClass().getMethods()) {
                                if (!"get".equals(m.getName()) || m.getParameterCount() != 1) continue;
                                Object arg = coerceIdentityArg(m.getParameterTypes()[0], directPlayer, profile);
                                if (arg == null) continue;
                                try {
                                    Object entry = m.invoke(opsList, arg);
                                    if (entry != null) return true;
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
                // keep going with additional fallbacks
            }

            if (hasServerProfilePermissions(src, directPlayer)) {
                return true;
            }

            // Final fallback: compare player name against currently loaded ops entries.
            // This handles environments where profile UUID matching can fail (e.g. changed identity mode).
            if (isOpByNameFallback(src, directPlayer)) {
                return true;
            }
            // Hard fallback: read ops.json directly from server working directory.
            // This avoids API/profile edge cases where OP checks report false unexpectedly.
            if (isOpByOpsFileFallback(directPlayer)) {
                return true;
            }
        }

        // try reflection-based hasPermissionLevel/hasPermission methods
        try {
            java.lang.reflect.Method method;
            try {
                method = src.getClass().getMethod("hasPermissionLevel", int.class);
            } catch (NoSuchMethodException e) {
                method = src.getClass().getMethod("hasPermission", int.class);
            }
            if ((boolean) method.invoke(src, requiredLevel)) {
                return true;
            }
        } catch (Exception ignored) {
            // if reflection fails we'll fall through to op-list check
        }

        // finally, explicitly check the server op list as a last resort
        try {
            ServerPlayer p = src.getPlayer();
            if (p != null) {
                if (isPlayerListedOp(src, p)) return true;
            }
        } catch (Exception ignored) {}

        return false;
    }

    public static boolean isLuckyPermsAvailable() {
        return luckypermsAvailable && luckypermsApi != null;
    }

    private static boolean isOpByNameFallback(CommandSourceStack src, ServerPlayer player) {
        try {
            if (src == null || src.getServer() == null || player == null) return false;
            Object playerList = src.getServer().getPlayerList();
            if (playerList == null) return false;

            java.lang.reflect.Method getOpsMethod = playerList.getClass().getMethod("getOps");
            Object opsList = getOpsMethod.invoke(playerList);
            if (opsList == null) return false;

            java.lang.reflect.Method getEntriesMethod = opsList.getClass().getMethod("getEntries");
            Object entriesObj = getEntriesMethod.invoke(opsList);
            if (!(entriesObj instanceof Iterable<?> iterable)) return false;

            String currentName = player.getScoreboardName();
            if (currentName == null || currentName.isBlank()) return false;

            for (Object entry : iterable) {
                if (entry == null) continue;
                try {
                    java.lang.reflect.Method getUserMethod = entry.getClass().getMethod("getUser");
                    Object userObj = getUserMethod.invoke(entry);
                    String opName = getProfileName(userObj);
                    if (opName != null && opName.equalsIgnoreCase(currentName)) {
                        return true;
                    }
                } catch (Exception ignored) {
                    // continue scanning other entries
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean isOpByOpsFileFallback(ServerPlayer player) {
        try {
            if (player == null) return false;
            String currentName = player.getScoreboardName();
            if (currentName == null || currentName.isBlank()) return false;

            long now = System.currentTimeMillis();
            if ((now - lastOpsFileReadAt) > OPS_FILE_CACHE_MS) {
                reloadOpsFileCache();
                lastOpsFileReadAt = now;
            }
            return cachedOpsNames.contains(currentName.toLowerCase());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void reloadOpsFileCache() {
        Set<String> names = new HashSet<>();
        try {
            Path opsPath = Paths.get("ops.json");
            if (!Files.exists(opsPath)) {
                cachedOpsNames = names;
                return;
            }
            String json = Files.readString(opsPath);
            JsonElement root = JsonParser.parseString(json);
            if (!(root instanceof JsonArray arr)) {
                cachedOpsNames = names;
                return;
            }
            for (JsonElement e : arr) {
                if (!(e instanceof JsonObject obj)) continue;
                JsonElement nameEl = obj.get("name");
                if (nameEl == null) continue;
                String n = nameEl.getAsString();
                if (n != null && !n.isBlank()) {
                    names.add(n.toLowerCase());
                }
            }
        } catch (Exception ignored) {
            // keep old cache if read fails
            return;
        }
        cachedOpsNames = names;
    }

    private static String getProfileName(Object profileObj) {
        if (profileObj == null) return null;
        try {
            java.lang.reflect.Method getName = profileObj.getClass().getMethod("getName");
            Object value = getName.invoke(profileObj);
            if (value instanceof String s && !s.isBlank()) {
                return s;
            }
        } catch (Exception ignored) {
        }
        return null;
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

    private static boolean hasServerProfilePermissions(CommandSourceStack src, ServerPlayer player) {
        try {
            if (src == null || src.getServer() == null || player == null) return false;
            Object server = src.getServer();
            Object profile = getPlayerProfileObject(player);
            if (profile == null) return false;

            for (java.lang.reflect.Method m : server.getClass().getMethods()) {
                if (!"getProfilePermissions".equals(m.getName()) || m.getParameterCount() != 1) continue;
                Object arg = coerceIdentityArg(m.getParameterTypes()[0], player, profile);
                if (arg == null) continue;
                try {
                    Object r = m.invoke(server, arg);
                    if (r instanceof Number n && n.intValue() > 0) return true;
                } catch (Throwable ignored) {}
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean isPlayerListedOp(CommandSourceStack src, ServerPlayer player) {
        try {
            if (src == null || src.getServer() == null || player == null) return false;
            Object playerList = src.getServer().getPlayerList();
            if (playerList == null) return false;
            Object profile = getPlayerProfileObject(player);

            for (java.lang.reflect.Method m : playerList.getClass().getMethods()) {
                String n = m.getName();
                if (!("isOp".equals(n) || "isOperator".equals(n))) continue;
                if (m.getParameterCount() != 1) continue;
                Object arg = coerceIdentityArg(m.getParameterTypes()[0], player, profile);
                if (arg == null) continue;
                try {
                    Object r = m.invoke(playerList, arg);
                    if (r instanceof Boolean b && b) return true;
                } catch (Throwable ignored) {}
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static Object getPlayerProfileObject(ServerPlayer player) {
        if (player == null) return null;
        try {
            java.lang.reflect.Method m = player.getClass().getMethod("getGameProfile");
            return m.invoke(player);
        } catch (Exception ignored) {}
        return null;
    }

    private static Object coerceIdentityArg(Class<?> paramType, ServerPlayer player, Object profile) {
        if (paramType == null || player == null) return null;
        if (profile != null && paramType.isInstance(profile)) return profile;
        if (paramType.isInstance(player)) return player;
        String name = player.getScoreboardName();

        if (paramType == String.class) {
            return name;
        }

        // Construct "NameAndId"-style identity records/classes when needed.
        try {
            java.lang.reflect.Constructor<?> c = paramType.getDeclaredConstructor(java.util.UUID.class, String.class);
            c.setAccessible(true);
            return c.newInstance(player.getUUID(), name);
        } catch (Exception ignored) {}
        try {
            java.lang.reflect.Constructor<?> c = paramType.getDeclaredConstructor(String.class, java.util.UUID.class);
            c.setAccessible(true);
            return c.newInstance(name, player.getUUID());
        } catch (Exception ignored) {}
        try {
            java.lang.reflect.Constructor<?> c = paramType.getDeclaredConstructor(String.class);
            c.setAccessible(true);
            return c.newInstance(name);
        } catch (Exception ignored) {}
        try {
            java.lang.reflect.Constructor<?> c = paramType.getDeclaredConstructor(java.util.UUID.class);
            c.setAccessible(true);
            return c.newInstance(player.getUUID());
        } catch (Exception ignored) {}
        return null;
    }

    private static void logDenied(String kind, ServerPlayer player, String details) {
        try {
            String name = player.getScoreboardName();
            long now = System.currentTimeMillis();
            String key = kind + ":" + name;
            Long last = LAST_DENY_LOG_AT.get(key);
            if (last != null && (now - last) < DENY_LOG_COOLDOWN_MS) {
                return;
            }
            LAST_DENY_LOG_AT.put(key, now);
            System.out.println("[StatusMod][perm] " + kind + " denied for " + name + ", " + details);
        } catch (Exception ignored) {
        }
    }
}
