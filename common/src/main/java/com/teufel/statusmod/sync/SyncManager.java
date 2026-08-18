package com.teufel.statusmod.sync;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.storage.ModConfig;
import com.teufel.statusmod.storage.PlayerSettings;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class SyncManager {
    private static final long SYNC_INTERVAL_MS = 30_000L;
    private static final int MAX_PLAYERS_PER_PUSH = 200;
    private static final int MAX_STATUS_LENGTH = 64;
    private static final int MAX_COLOR_LENGTH = 32;
    private static final int MAX_USERNAME_LENGTH = 16;
    private static final java.util.regex.Pattern UUID_PATTERN =
            java.util.regex.Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Gson GSON = new Gson();
    private static volatile boolean started = false;
    private static volatile Map<String, String> onlineNames = new HashMap<>();

    private SyncManager() {}

    public static void updateOnlineNames(MinecraftServer server) {
        if (server == null) return;
        try {
            Map<String, String> names = new HashMap<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                names.put(p.getUUID().toString(), p.getScoreboardName());
            }
            onlineNames = names;
        } catch (Exception ignored) {}
    }

    public static void start() {
        if (started) return;
        ModConfig config = StatusMod.getConfig();
        if (config == null) return;
        if (!config.isEnabled("sync")) return;
        if (config.dashboardUrl == null || config.dashboardUrl.trim().isEmpty()) return;
        if (config.serverId == null || config.serverId.isEmpty()) return;
        if (config.apiKey == null || config.apiKey.isEmpty()) return;

        started = true;
        Thread thread = new Thread(SyncManager::loop, "statusmod-sync");
        thread.setDaemon(true);
        thread.start();
        System.out.println("[StatusMod] Sync enabled. Push interval: " + (SYNC_INTERVAL_MS / 1000) + "s");
    }

    public static void retryStart() {
        if (started) return;
        start();
    }

    private static void loop() {
        while (true) {
            try {
                Thread.sleep(SYNC_INTERVAL_MS);
            } catch (InterruptedException e) {
                return;
            }
            try {
                push();
            } catch (Exception e) {
                System.out.println("[StatusMod] Sync push failed: " + e.getMessage());
            }
            try {
                pull();
            } catch (Exception e) {
                System.out.println("[StatusMod] Sync pull failed: " + e.getMessage());
            }
        }
    }

    private static void push() {
        ModConfig config = StatusMod.getConfig();
        if (config == null || StatusMod.storage == null) return;
        String dashboardUrl = trimTrailingSlash(config.dashboardUrl);
        if (!isSecureUrl(dashboardUrl)) return;
        String endpoint = dashboardUrl + "/api/players/" + config.serverId + "/sync";
        String apiKey = config.apiKey;

        JsonObject payload = new JsonObject();

        JsonArray players = new JsonArray();
        Map<String, PlayerSettings> snapshot = StatusMod.storage.getAllSnapshot();
        int count = 0;
        for (Map.Entry<String, PlayerSettings> e : snapshot.entrySet()) {
            if (count >= MAX_PLAYERS_PER_PUSH) break;
            String uuid = e.getKey();
            PlayerSettings ps = e.getValue();
            JsonObject entry = new JsonObject();
            entry.addProperty("uuid", uuid);
            String name = onlineNames.get(uuid);
            if (name != null && !name.isEmpty()) {
                entry.addProperty("username", name);
            }
            entry.addProperty("status", ps.status == null ? "" : ps.status);
            entry.addProperty("color", ps.color == null ? "reset" : ps.color);

            JsonObject settings = new JsonObject();
            settings.addProperty("brackets", ps.brackets);
            settings.addProperty("beforeName", ps.beforeName);
            if (ps.fontStyle != null) settings.addProperty("fontStyle", ps.fontStyle);
            if (ps.statusWords > 0) settings.addProperty("statusWords", ps.statusWords);
            if (ps.statusByWorld != null) settings.add("statusByWorld", GSON.toJsonTree(ps.statusByWorld));
            if (ps.colorByWorld != null) settings.add("colorByWorld", GSON.toJsonTree(ps.colorByWorld));

            ModConfig.StaffBadge badge = config.staffBadges == null ? null : config.staffBadges.get(uuid);
            if (badge != null) {
                JsonObject b = new JsonObject();
                b.addProperty("text", badge.text);
                b.addProperty("color", badge.color);
                b.addProperty("brackets", badge.brackets);
                settings.add("badge", b);
            }
            entry.add("settings", settings);
            players.add(entry);
            count++;
        }
        payload.add("players", players);

        JsonArray muted = new JsonArray();
        Map<String, Long> mutedAll = StatusMod.mutedPlayers == null
                ? new HashMap<>() : StatusMod.mutedPlayers.getAllMuted();
        for (Map.Entry<String, Long> e : mutedAll.entrySet()) {
            JsonObject m = new JsonObject();
            m.addProperty("uuid", e.getKey());
            m.addProperty("muted_until", e.getValue());
            muted.add(m);
        }
        payload.add("muted", muted);

        JsonArray blocked = new JsonArray();
        Set<String> blockedAll = StatusMod.blockedPlayers == null
                ? java.util.Collections.emptySet() : StatusMod.blockedPlayers.getBlockedPlayers();
        for (String uuid : blockedAll) {
            JsonObject b = new JsonObject();
            b.addProperty("uuid", uuid);
            blocked.add(b);
        }
        payload.add("blocked", blocked);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                System.out.println("[StatusMod] Sync push status " + response.statusCode() + ": " + response.body());
            }
        } catch (java.io.IOException | InterruptedException e) {
            System.out.println("[StatusMod] Sync push exception: " + e.getMessage());
        }
    }

    private static void pull() {
        ModConfig config = StatusMod.getConfig();
        if (config == null || StatusMod.mutedPlayers == null || StatusMod.blockedPlayers == null) return;
        String dashboardUrl = trimTrailingSlash(config.dashboardUrl);
        if (!isSecureUrl(dashboardUrl)) {
            System.out.println("[StatusMod] Sync pull skipped: dashboardUrl must use HTTPS (or localhost for dev)");
            return;
        }
        String since = config.lastSyncAtMs > 0L ? "?since=" + java.time.Instant.ofEpochMilli(config.lastSyncAtMs) : "";
        String endpoint = dashboardUrl + "/api/players/" + config.serverId + "/sync" + since;

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("x-api-key", config.apiKey)
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException | InterruptedException e) {
            System.out.println("[StatusMod] Sync pull exception: " + e.getMessage());
            return;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) return;

        JsonObject json;
        try {
            json = JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (Exception e) {
            System.out.println("[StatusMod] Sync pull: invalid JSON response");
            return;
        }
        if (!json.has("muted") || !json.has("blocked") || !json.has("server_time")) return;

        for (JsonElement el : json.getAsJsonArray("muted")) {
            try {
                JsonObject m = el.getAsJsonObject();
                String uuid = m.get("uuid").getAsString();
                if (!isValidUuid(uuid)) continue;
                long until = java.time.Instant.parse(m.get("muted_until").getAsString()).toEpochMilli();
                StatusMod.mutedPlayers.muteUntil(uuid, until);
            } catch (Exception ignored) {}
        }

        for (JsonElement el : json.getAsJsonArray("blocked")) {
            try {
                JsonObject b = el.getAsJsonObject();
                String uuid = b.get("uuid").getAsString();
                if (!isValidUuid(uuid)) continue;
                StatusMod.blockedPlayers.block(uuid);
            } catch (Exception ignored) {}
        }

        for (JsonElement el : json.getAsJsonArray("players")) {
            try {
                JsonObject p = el.getAsJsonObject();
                String uuid = p.has("uuid") ? p.get("uuid").getAsString() : "";
                if (!isValidUuid(uuid)) continue;
                String status = p.has("status") && !p.get("status").isJsonNull()
                        ? truncate(p.get("status").getAsString(), MAX_STATUS_LENGTH) : "";
                String color = p.has("color") && !p.get("color").isJsonNull()
                        ? truncate(p.get("color").getAsString(), MAX_COLOR_LENGTH) : "reset";
                if (StatusMod.storage == null) continue;
                PlayerSettings ps = StatusMod.storage.forPlayer(uuid);
                if (status != null && !status.isEmpty() && !status.equals(ps.status)) {
                    ps.status = status;
                    ps.color = color;
                    ps.lastStatusChangeAtMs = System.currentTimeMillis();
                    StatusMod.storage.put(uuid, ps);
                }
            } catch (Exception ignored) {}
        }

        try {
            config.lastSyncAtMs = java.time.Instant.parse(json.get("server_time").getAsString()).toEpochMilli();
            config.save();
        } catch (Exception ignored) {}
    }

    private static boolean isSecureUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase();
        if (lower.startsWith("https://")) return true;
        if (lower.startsWith("http://localhost") || lower.startsWith("http://127.")) return true;
        return false;
    }

    private static boolean isValidUuid(String s) {
        return s != null && UUID_PATTERN.matcher(s).matches();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String trimTrailingSlash(String url) {
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
