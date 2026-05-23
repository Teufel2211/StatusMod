package com.teufel.statusmod.util;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.function.Supplier;

public final class CommandUtil {
    private CommandUtil() {}

    public static void sendSuccess(CommandSourceStack src, Component msg, boolean broadcast) {
        if (src == null || msg == null) return;
        // Newer mappings use Supplier<Component>
        try {
            Method m = src.getClass().getMethod("sendSuccess", Supplier.class, boolean.class);
            m.invoke(src, (Supplier<Component>) () -> msg, broadcast);
            return;
        } catch (Throwable ignored) {
        }
        // Older mappings use direct Component
        try {
            Method m = src.getClass().getMethod("sendSuccess", Component.class, boolean.class);
            m.invoke(src, msg, broadcast);
            return;
        } catch (Throwable ignored) {
        }
        // Fallback for legacy mappings
        try {
            Method m = src.getClass().getMethod("sendSystemMessage", Component.class);
            m.invoke(src, msg);
        } catch (Throwable ignored) {
        }
    }
}
