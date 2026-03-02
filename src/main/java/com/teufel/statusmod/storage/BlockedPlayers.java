package com.teufel.statusmod.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
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
        return blockedUuids.contains(uuid);
    }

    public synchronized void block(String uuid) {
        if (blockedUuids.add(uuid)) {
            save();
        }
    }

    public synchronized void unblock(String uuid) {
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
            FileReader fr = new FileReader(file);
            blockedUuids = gson.fromJson(fr, t);
            if (blockedUuids == null) blockedUuids = new HashSet<>();
            fr.close();
        } catch (Exception e) {
            System.err.println("[StatusMod] Error loading blocked players:");
            e.printStackTrace();
        }
    }

    public synchronized void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (blockedUuids == null) blockedUuids = new HashSet<>();
            FileWriter fw = new FileWriter(file);
            gson.toJson(blockedUuids, fw);
            fw.close();
        } catch (Exception e) {
            System.err.println("[StatusMod] Error saving blocked players:");
            e.printStackTrace();
        }
    }
}
