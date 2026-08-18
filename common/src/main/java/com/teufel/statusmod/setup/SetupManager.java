package com.teufel.statusmod.setup;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.storage.ModConfig;
import com.teufel.statusmod.sync.SyncManager;
import com.teufel.statusmod.util.CodeGenerator;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

public final class SetupManager {
    private static final int CODE_LENGTH = 16;
    private static final long PENDING_POLL_INTERVAL_MS = 30_000;
    private static final long PENDING_POLL_TIMEOUT_MS = 24 * 60 * 60 * 1000;

    private SetupManager() {}

    public static void runIfNeeded() {
        ModConfig config = StatusMod.getConfig();
        if (config == null) return;
        if (config.dashboardUrl == null || config.dashboardUrl.trim().isEmpty()) return;
        if (config.setupSecret == null || config.setupSecret.trim().isEmpty()) return;

        boolean needsKey = config.apiKey == null || config.apiKey.isEmpty();

        if (config.serverId != null && !config.serverId.isEmpty()) {
            if (needsKey) {
                startPendingKeyPoll(config);
            }
            return;
        }

        String dashboardUrl = CodeGenerator.trimTrailingSlash(config.dashboardUrl);
        String serverId = UUID.randomUUID().toString();
        String code = CodeGenerator.generate(CODE_LENGTH);

        Thread thread = new Thread(() -> {
            try {
                if (register(serverId, code, dashboardUrl, config.setupSecret)) {
                    config.serverId = serverId;
                    config.save();
                    System.out.println("==============================================");
                    System.out.println("[StatusMod] SERVER SETUP");
                    System.out.println("[StatusMod] Dashboard: " + dashboardUrl + "/setup");
                    System.out.println("[StatusMod] Setup-Code: " + code);
                    System.out.println("[StatusMod] Code expires in 24 hours. Open the URL and enter the code to claim this server.");
                    System.out.println("[StatusMod] Waiting for claim to fetch the API key automatically...");
                    System.out.println("==============================================");
                    if (needsKey) {
                        startPendingKeyPoll(config);
                    }
                } else {
                    System.out.println("[StatusMod] Setup registration failed. The code will be retried on next server start.");
                }
            } catch (Exception e) {
                System.out.println("[StatusMod] Setup error: " + e.getMessage());
            }
        }, "statusmod-setup");
        thread.setDaemon(true);
        thread.start();
    }

    private static void startPendingKeyPoll(ModConfig config) {
        Thread thread = new Thread(() -> pollPendingKey(config), "statusmod-key-poll");
        thread.setDaemon(true);
        thread.start();
    }

    private static void pollPendingKey(ModConfig config) {
        long deadline = System.currentTimeMillis() + PENDING_POLL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(PENDING_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                return;
            }
            try {
                String apiKey = fetchPendingKey(CodeGenerator.trimTrailingSlash(config.dashboardUrl), config.serverId, config.setupSecret);
                if (apiKey != null) {
                    config.apiKey = apiKey;
                    config.save();
                    System.out.println("[StatusMod] API-Key automatically fetched. Server setup complete.");
                    SyncManager.retryStart();
                    return;
                }
            } catch (Exception e) {
                System.out.println("[StatusMod] API-Key fetch error: " + e.getMessage());
            }
        }
        System.out.println("[StatusMod] No API-Key received within 24h. The setup code has expired.");
    }

    private static String fetchPendingKey(String dashboardUrl, String serverId, String setupSecret) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(dashboardUrl + "/api/auth/setup/pending-key?server_id=" + serverId))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + setupSecret)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!json.has("pending") || !json.get("pending").getAsBoolean()) {
                return null;
            }
            return json.get("api_key").getAsString();
        } catch (Exception e) {
            System.out.println("[StatusMod] API-Key fetch exception: " + e);
            return null;
        }
    }

    private static boolean register(String serverId, String code, String dashboardUrl, String setupSecret) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            JsonObject payload = new JsonObject();
            payload.addProperty("server_id", serverId);
            payload.addProperty("code", code);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(dashboardUrl + "/api/auth/setup/init"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + setupSecret)
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            System.out.println("[StatusMod] Setup registration exception: " + e);
            return false;
        }
    }
}
