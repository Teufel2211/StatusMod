package com.teufel.statusmod.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class CustomPresets {
    private final File file;
    private Map<String, CustomPreset> presets = new LinkedHashMap<>();
    private final Gson gson = new Gson();

    public CustomPresets() {
        File configDir = new File("config/statusmod");
        configDir.mkdirs();
        file = new File(configDir, "custom_presets.json");
        load();
    }

    public synchronized boolean add(String name, String status, String color, String creatorUuid) {
        if (name == null || name.isBlank() || status == null || status.isBlank()) return false;
        status = status.replaceAll("\u00A7.", "").replace("\u00A7", "");
        if (status.length() > 64) status = status.substring(0, 64);
        String key = name.trim().toLowerCase();
        if (presets.containsKey(key)) return false;
        presets.put(key, new CustomPreset(status.trim(), color == null ? "reset" : color, creatorUuid));
        save();
        return true;
    }

    public synchronized boolean remove(String name, String requesterUuid) {
        if (name == null || name.isBlank()) return false;
        String key = name.trim().toLowerCase();
        CustomPreset p = presets.get(key);
        if (p == null) return false;
        if (requesterUuid != null && (p.creator == null || !p.creator.equals(requesterUuid))) return false;
        presets.remove(key);
        save();
        return true;
    }

    public synchronized boolean adminRemove(String name) {
        return remove(name, null);
    }

    public synchronized CustomPreset get(String name) {
        if (name == null || name.isBlank()) return null;
        return presets.get(name.trim().toLowerCase());
    }

    public synchronized Collection<String> getNames() {
        return new java.util.ArrayList<>(presets.keySet());
    }

    public synchronized Map<String, CustomPreset> getAll() {
        return new LinkedHashMap<>(presets);
    }

    public synchronized void load() {
        try {
            if (!file.exists()) return;
            Type t = new TypeToken<Map<String, CustomPreset>>(){}.getType();
            try (Reader fr = Files.newBufferedReader(file.toPath())) {
                presets = gson.fromJson(fr, t);
            }
            if (presets == null) presets = new LinkedHashMap<>();
            presets.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
        } catch (Exception e) {
            System.err.println("[StatusMod] Error loading custom presets:");
            e.printStackTrace();
            safeBackupCorrupted(file.toPath(), "custom_presets.corrupt-" + System.currentTimeMillis() + ".json");
            presets = new LinkedHashMap<>();
        }
    }

    public synchronized void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (presets == null) presets = new LinkedHashMap<>();
            Path target = file.toPath();
            Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
            try (Writer fw = Files.newBufferedWriter(tmp)) {
                gson.toJson(presets, fw);
            }
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            System.err.println("[StatusMod] Error saving custom presets:");
            e.printStackTrace();
        }
    }

    private void safeBackupCorrupted(Path source, String backupName) {
        try {
            if (!Files.exists(source)) return;
            Path backup = source.resolveSibling(backupName);
            Files.copy(source, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {}
    }

    public static class CustomPreset {
        public String status;
        public String color;
        public String creator;

        public CustomPreset() {}

        public CustomPreset(String status, String color, String creator) {
            this.status = status;
            this.color = color;
            this.creator = creator;
        }
    }
}
