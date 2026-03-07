package com.teufel.statusmod.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import com.teufel.statusmod.util.FontMapper;

public class SettingsStorage {
    private final File file;
    private Map<String, PlayerSettings> map = new HashMap<>();
    private final Gson gson = new Gson();

    public SettingsStorage() {
        File configDir = new File("config/statusmod");
        configDir.mkdirs();
        file = new File(configDir, "players.json");
        load();
        ServerLifecycleEvents.SERVER_STOPPING.register((MinecraftServer server) -> save());
    }

    public synchronized PlayerSettings forPlayer(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return new PlayerSettings();
        }
        // If a player does not yet have settings, create defaults, persist them immediately
        PlayerSettings s = map.get(uuid);
        if (s == null) {
            s = new PlayerSettings();
            // apply configurable defaults if available
            try {
                if (com.teufel.statusmod.StatusMod.config != null &&
                        com.teufel.statusmod.StatusMod.config.defaultColor != null) {
                    s.color = com.teufel.statusmod.StatusMod.config.defaultColor;
                }
            } catch (Exception ignored) {}
            sanitizeSettings(s);
            map.put(uuid, s);
            save();
        }
        return s;
    }

    public synchronized void put(String uuid, PlayerSettings s) {
        if (uuid == null || uuid.isBlank() || s == null) return;
        sanitizeSettings(s);
        map.put(uuid, s);
        save();
    }

    public synchronized void load() {
        try {
            if (!file.exists()) return;
            Type t = new TypeToken<Map<String, PlayerSettings>>(){}.getType();
            try (Reader fr = Files.newBufferedReader(file.toPath())) {
                map = gson.fromJson(fr, t);
            }
            if (map == null) {
                map = new HashMap<>();
            }
            // Migration: ensure fields have sensible defaults when older JSON is missing fields
            boolean migrated = false;
            for (Map.Entry<String, PlayerSettings> e : map.entrySet()) {
                PlayerSettings ps = e.getValue();
                if (ps == null) {
                    ps = new PlayerSettings();
                    e.setValue(ps);
                    migrated = true;
                }
                if (sanitizeSettings(ps)) migrated = true;
            }
            if (migrated) save();
        } catch (Exception e) {
            System.err.println("[StatusMod] Failed to load players.json, keeping in-memory defaults");
            e.printStackTrace();
            safeBackupCorrupted(file.toPath(), "players.corrupt-" + System.currentTimeMillis() + ".json");
            map = new HashMap<>();
        }
    }

    public synchronized void save() {
        try {
            // ensure parent directory exists
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (map == null) map = new HashMap<>();

            Path target = file.toPath();
            Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
            try (Writer fw = Files.newBufferedWriter(tmp)) {
                gson.toJson(map, fw);
            }

            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean sanitizeSettings(PlayerSettings ps) {
        boolean changed = false;
        if (ps.statusWords <= 0) {
            ps.statusWords = 1;
            changed = true;
        }
        if (ps.status == null) {
            ps.status = "";
            changed = true;
        }
        if (ps.color == null || ps.color.isEmpty()) {
            try {
                ps.color = (com.teufel.statusmod.StatusMod.config != null &&
                        com.teufel.statusmod.StatusMod.config.defaultColor != null)
                        ? com.teufel.statusmod.StatusMod.config.defaultColor
                        : "reset";
            } catch (Exception ignored) {
                ps.color = "reset";
            }
            changed = true;
        }
        String normalizedFont = FontMapper.normalizeStyle(ps.fontStyle);
        if (ps.fontStyle == null || !ps.fontStyle.equals(normalizedFont)) {
            ps.fontStyle = normalizedFont;
            changed = true;
        }
        return changed;
    }

    private void safeBackupCorrupted(Path source, String backupName) {
        try {
            if (!Files.exists(source)) return;
            Path backup = source.resolveSibling(backupName);
            Files.copy(source, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
        }
    }
}
