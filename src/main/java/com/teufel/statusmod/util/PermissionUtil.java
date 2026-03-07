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

        // try the standard permission check available on CommandSourceStack first
        try {
            if (src.hasPermission(requiredLevel)) {
                return true;
            }
            // Robust fallback for mixed server setups:
            // if configured level is higher than vanilla admin baseline, still allow
            // sources that can execute level-2 commands (typical OP/admin level).
            if (requiredLevel > 2 && src.hasPermission(2)) {
                return true;
            }
        } catch (NoSuchMethodError e) {
            // ignore and try fallbacks below
        } catch (Throwable t) {
            System.err.println("[StatusMod] unexpected error calling src.hasPermission: " + t);
        }
        
        // Direct vanilla OP list check: if player is OP at all, allow admin commands
        // even when their OP level is below configured adminOpLevel.
        ServerPlayer directPlayer = null;
        try { directPlayer = src.getPlayer(); } catch (Exception ignored) {}
        if (directPlayer != null) {
            // Direct player-level permission probe. Some environments expose this
            // reliably even when CommandSourceStack hasPermission(...) is inconsistent.
            try {
                if (directPlayer.hasPermissions(requiredLevel)) {
                    return true;
                }
                if (requiredLevel > 2 && directPlayer.hasPermissions(2)) {
                    return true;
                }
            } catch (Throwable ignored) {
                // keep going with additional fallbacks
            }
            try {
                if (src.getServer() != null && src.getServer().getPlayerList().isOp(directPlayer.getGameProfile())) {
                    return true;
                }
            } catch (Throwable ignored) {
                // keep going with additional fallbacks
            }
            // Alternate OP-list probe for environments where isOp(...) is unreliable.
            try {
                Object playerList = src.getServer() == null ? null : src.getServer().getPlayerList();
                if (playerList != null) {
                    java.lang.reflect.Method getOps = playerList.getClass().getMethod("getOps");
                    Object opsList = getOps.invoke(playerList);
                    if (opsList != null) {
                        java.lang.reflect.Method get = opsList.getClass().getMethod("get", com.mojang.authlib.GameProfile.class);
                        Object entry = get.invoke(opsList, directPlayer.getGameProfile());
                        if (entry != null) {
                            return true;
                        }
                    }
                }
            } catch (Throwable ignored) {
                // keep going with additional fallbacks
            }

            try {
                if (src.getServer() != null && src.getServer().getProfilePermissions(directPlayer.getGameProfile()) > 0) {
                    return true;
                }
            } catch (Throwable ignored) {
                // keep going with additional fallbacks
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
                Object playerList = src.getServer().getPlayerList();
                try {
                    java.lang.reflect.Method m = playerList.getClass().getMethod("isOp", com.mojang.authlib.GameProfile.class);
                    if ((boolean) m.invoke(playerList, p.getGameProfile())) {
                        return true;
                    }
                } catch (NoSuchMethodException ex) {
                    java.lang.reflect.Method m2 = playerList.getClass().getMethod("isOperator", com.mojang.authlib.GameProfile.class);
                    if ((boolean) m2.invoke(playerList, p.getGameProfile())) {
                        return true;
                    }
                }
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
