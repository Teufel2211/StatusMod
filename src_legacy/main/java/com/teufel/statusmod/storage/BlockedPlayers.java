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
import java.util.HashSet;
import java.util.Set;

/**
 * Persistent storage for blocked players.
 * Stores UUIDs of players who have been blocked from using the status mod.
 */
public class BlockedPlayers {
    private final File file;
    private Set<String> blockedUuids = new HashSet<>();
    private final Gson gson = new Gson();

    public BlockedPlayers() {
        File configDir = new File("config/statusmod");
        configDir.mkdirs();
        file = new File(configDir, "blocked_players.json");
        load();
    }

    public synchronized boolean isBlocked(String uuid) {
        if (uuid == null || uuid.isBlank()) return false;
        return blockedUuids.contains(uuid);
    }

    public synchronized void block(String uuid) {
        if (uuid == null || uuid.isBlank()) return;
        if (blockedUuids.add(uuid)) {
            save();
        }
    }

    public synchronized void unblock(String uuid) {
        if (uuid == null || uuid.isBlank()) return;
        if (blockedUuids.remove(uuid)) {
            save();
        }
    }

    public synchronized Set<String> getBlockedPlayers() {
        return new HashSet<>(blockedUuids);
    }

    public synchronized void load() {
        try {
            if (!file.exists()) return;
            Type t = new TypeToken<Set<String>>(){}.getType();
            try (Reader fr = Files.newBufferedReader(file.toPath())) {
                blockedUuids = gson.fromJson(fr, t);
            }
            if (blockedUuids == null) blockedUuids = new HashSet<>();
            blockedUuids.removeIf(uuid -> uuid == null || uuid.isBlank());
        } catch (Exception e) {
            System.err.println("[StatusMod] Error loading blocked players:");
            e.printStackTrace();
            safeBackupCorrupted(file.toPath(), "blocked_players.corrupt-" + System.currentTimeMillis() + ".json");
            blockedUuids = new HashSet<>();
        }
    }

    public synchronized void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (blockedUuids == null) blockedUuids = new HashSet<>();

            Path target = file.toPath();
            Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
            try (Writer fw = Files.newBufferedWriter(tmp)) {
                gson.toJson(blockedUuids, fw);
            }
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            System.err.println("[StatusMod] Error saving blocked players:");
            e.printStackTrace();
        }
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
