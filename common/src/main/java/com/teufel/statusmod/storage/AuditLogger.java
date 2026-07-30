package com.teufel.statusmod.storage;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AuditLogger {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static File logFile;

    public static synchronized void init() {
        File configDir = new File("config/statusmod");
        configDir.mkdirs();
        logFile = new File(configDir, "audit.log");
    }

    public static synchronized void log(String who, String action, String target, String detail) {
        if (logFile == null) init();
        try {
            String line = String.format("[%s] %s | %s | %s | %s%n",
                LocalDateTime.now().format(FMT),
                safe(who), safe(action), safe(target), safe(detail));
            Path p = logFile.toPath();
            Files.writeString(p, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[StatusMod] Failed to write audit log: " + e.getMessage());
        }
    }

    public static void logSet(String who, String target, String status, String color) {
        log(who, "SET", target, "status=" + status + " color=" + color);
    }

    public static void logClear(String who, String target) {
        log(who, "CLEAR", target, "");
    }

    public static void logBlock(String who, String target) {
        log(who, "BLOCK", target, "");
    }

    public static void logUnblock(String who, String target) {
        log(who, "UNBLOCK", target, "");
    }

    public static void logMute(String who, String target, int minutes) {
        log(who, "MUTE", target, minutes + "min");
    }

    public static void logUnmute(String who, String target) {
        log(who, "UNMUTE", target, "");
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.replace("|", "/").replace("\n", " ").replace("\r", " ").replace("\t", " ").replaceAll("\\p{Cntrl}", "?");
    }
}
