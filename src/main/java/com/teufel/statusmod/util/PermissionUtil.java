package com.teufel.statusmod.util;

import net.minecraft.commands.CommandSourceStack;
import com.teufel.statusmod.StatusMod;
import net.minecraft.server.level.ServerPlayer;

/**
 * Permission utility that integrates with LuckyPerms if available.
 * Falls back to basic permission checking if LuckyPerms is not installed.
 * Uses reflection to avoid compile-time dependency on LuckyPerms.
 */
public class PermissionUtil {
    private static boolean luckypermsAvailable = false;
    private static Object luckypermsApi = null;

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
            return lpAllowed || op;
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
            return lpAllowed || op;
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
        // try the standard permission check available on CommandSourceStack first
        try {
            if (src.hasPermission(StatusMod.config.adminOpLevel)) {
                return true;
            }
        } catch (NoSuchMethodError e) {
            // ignore and try fallbacks below
        } catch (Throwable t) {
            System.err.println("[StatusMod] unexpected error calling src.hasPermission: " + t);
        }

        // try reflection-based hasPermissionLevel/hasPermission methods
        try {
            java.lang.reflect.Method method;
            try {
                method = src.getClass().getMethod("hasPermissionLevel", int.class);
            } catch (NoSuchMethodException e) {
                method = src.getClass().getMethod("hasPermission", int.class);
            }
            if ((boolean) method.invoke(src, StatusMod.config.adminOpLevel)) {
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
}
