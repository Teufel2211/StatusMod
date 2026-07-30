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
import java.util.HashMap;
import java.util.Map;

public class MutedPlayers {
    private final File file;
    private Map<String, Long> mutedUntil = new HashMap<>();
    private final Gson gson = new Gson();

    public MutedPlayers() {
        File configDir = new File("config/statusmod");
        configDir.mkdirs();
        file = new File(configDir, "muted_players.json");
        load();
    }

    public synchronized boolean isMuted(String uuid) {
        if (uuid == null || uuid.isBlank()) return false;
        Long until = mutedUntil.get(uuid);
        if (until == null) return false;
        if (System.currentTimeMillis() >= until) {
            mutedUntil.remove(uuid);
            save();
            return false;
        }
        return true;
    }

    public synchronized long getMutedUntil(String uuid) {
        if (uuid == null || uuid.isBlank()) return 0;
        Long until = mutedUntil.get(uuid);
        if (until == null) return 0;
        if (System.currentTimeMillis() >= until) {
            mutedUntil.remove(uuid);
            save();
            return 0;
        }
        return until;
    }

    public synchronized void mute(String uuid, int minutes) {
        if (uuid == null || uuid.isBlank()) return;
        mutedUntil.put(uuid, System.currentTimeMillis() + (minutes * 60L * 1000L));
        save();
    }

    public synchronized void unmute(String uuid) {
        if (uuid == null || uuid.isBlank()) return;
        if (mutedUntil.remove(uuid) != null) save();
    }

    public synchronized Map<String, Long> getAllMuted() {
        purgeExpired();
        return new HashMap<>(mutedUntil);
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        mutedUntil.entrySet().removeIf(e -> now >= e.getValue());
    }

    public synchronized void load() {
        try {
            if (!file.exists()) return;
            Type t = new TypeToken<Map<String, Long>>(){}.getType();
            try (Reader fr = Files.newBufferedReader(file.toPath())) {
                mutedUntil = gson.fromJson(fr, t);
            }
            if (mutedUntil == null) mutedUntil = new HashMap<>();
            mutedUntil.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null || System.currentTimeMillis() >= e.getValue());
            save();
        } catch (Exception e) {
            System.err.println("[StatusMod] Error loading muted players:");
            e.printStackTrace();
            safeBackupCorrupted(file.toPath(), "muted_players.corrupt-" + System.currentTimeMillis() + ".json");
            mutedUntil = new HashMap<>();
        }
    }

    public synchronized void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            purgeExpired();
            if (mutedUntil == null) mutedUntil = new HashMap<>();
            Path target = file.toPath();
            Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
            try (Writer fw = Files.newBufferedWriter(tmp)) {
                gson.toJson(mutedUntil, fw);
            }
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            System.err.println("[StatusMod] Error saving muted players:");
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
}
