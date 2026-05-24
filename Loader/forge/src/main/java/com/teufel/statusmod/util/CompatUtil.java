package com.teufel.statusmod.util;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class CompatUtil {
    private CompatUtil() {}

    public static String getWorldKey(ServerPlayer player) {
        if (player == null) return null;
        Object level = null;
        try {
            Method m = player.getClass().getMethod("level");
            level = m.invoke(player);
        } catch (Throwable ignored) {
        }
        if (level == null) {
            try {
                Method m = player.getClass().getMethod("getLevel");
                level = m.invoke(player);
            } catch (Throwable ignored) {
            }
        }
        if (level == null) {
            try {
                Field f = player.getClass().getDeclaredField("level");
                f.setAccessible(true);
                level = f.get(player);
            } catch (Throwable ignored) {
            }
        }
        if (level == null) return null;
        try {
            Method m = level.getClass().getMethod("dimension");
            Object dim = m.invoke(level);
            return dim == null ? null : String.valueOf(dim);
        } catch (Throwable ignored) {
        }
        return null;
    }
}
