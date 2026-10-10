package com.teufel.fleet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pulls the global fleet config (dashboard_url + setup_secret) from the
 * dashboard and applies it to StatusMod's config file. serverId/apiKey are
 * per-server identity and are NEVER touched. Runs standalone (no StatusMod
 * dependency): reads/writes config/statusmod/config.json directly, preserving
 * all unknown fields.
 */
public final class FleetManager {
    private static final long DEFAULT_PULL_INTERVAL_MS = 60_000L;
    private static final long MIN_PULL_INTERVAL_MS = 15_000L;
    private static final long MAX_PULL_INTERVAL_MS = 3_600_000L;
    private static final long LOOP_TICK_MS = 5_000L;
    private static final int MAX_BODY_BYTES = 8 * 1024 * 1024;
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static volatile boolean started = false;
    private static volatile boolean pullNow = false;
    public static volatile String lastPullInfo = "-";
    public static volatile String lastChangeInfo = "-";

    private FleetManager() {}

    public static void start() {
        if (started) return;
        started = true;
        Thread t = new Thread(FleetManager::loop, "fleet-sync");
        t.setDaemon(true);
        t.start();
    }

    /** Manual trigger via /fleet apply (next loop iteration pulls immediately). */
    public static void requestSync() {
        pullNow = true;
    }

    private static void loop() {
        long lastPull = 0L;
        long interval = DEFAULT_PULL_INTERVAL_MS;
        while (true) {
            try {
                Thread.sleep(LOOP_TICK_MS);
            } catch (InterruptedException e) {
                return;
            }
            long now = System.currentTimeMillis();
            interval = pullIntervalMs();
            if (pullNow || (now - lastPull) >= interval) {
                pullNow = false;
                lastPull = now;
                try {
                    pull();
                } catch (Exception e) {
                    lastPullInfo = "ex: " + e.getMessage();
                    System.out.println("[Fleet] pull failed: " + e.getMessage());
                }
            }
        }
    }

    private static Path configPath() {
        return Path.of("config", "statusmodfleet", "config.json");
    }

    private static Path statusConfigPath() {
        return Path.of("config", "statusmod", "config.json");
    }

    /**
     * Fleet's own config (fleet-managed values live here, NOT in StatusMod's
     * config). First start bootstraps dashboardUrl/setupSecret from StatusMod's
     * config so existing setups migrate seamlessly.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> readFleetConfig() {
        try {
            Path p = configPath();
            if (Files.exists(p)) {
                Object parsed = GSON.fromJson(Files.readString(p), Map.class);
                if (parsed instanceof Map<?, ?> map) {
                    Map<String, Object> out = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : map.entrySet()) {
                        if (e.getKey() instanceof String k) out.put(k, e.getValue());
                    }
                    return out;
                }
            }
        } catch (Exception e) {
            System.out.println("[Fleet] cannot read fleet config: " + e.getMessage());
        }
        // Bootstrap from StatusMod's config (one-time import).
        Map<String, Object> boot = new LinkedHashMap<>();
        boot.put("enabled", true);
        boot.put("pullIntervalSecs", 60);
        boot.put("dashboardUrl", "");
        boot.put("setupSecret", "");
        try {
            Path sp = statusConfigPath();
            if (Files.exists(sp)) {
                Object parsed = GSON.fromJson(Files.readString(sp), Map.class);
                if (parsed instanceof Map<?, ?> map) {
                    Object du = map.get("dashboardUrl");
                    Object ss = map.get("setupSecret");
                    if (du != null) boot.put("dashboardUrl", String.valueOf(du).trim());
                    if (ss != null) boot.put("setupSecret", String.valueOf(ss).trim());
                }
            }
        } catch (Exception ignored) {}
        try {
            writeFleetConfig(boot);
        } catch (Exception e) {
            System.out.println("[Fleet] cannot write fleet config: " + e.getMessage());
        }
        return boot;
    }

    static void writeFleetConfig(Map<String, Object> cfg) throws java.io.IOException {
        Path target = configPath();
        if (target.getParent() != null) Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        Files.writeString(tmp, GSON.toJson(cfg));
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ignored) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static long pullIntervalMs() {
        try {
            Object v = readFleetConfig().get("pullIntervalSecs");
            long secs = v instanceof Number n ? n.longValue() : 60L;
            return Math.max(MIN_PULL_INTERVAL_MS, Math.min(MAX_PULL_INTERVAL_MS, secs * 1000L));
        } catch (Exception ignored) {
            return DEFAULT_PULL_INTERVAL_MS;
        }
    }

    private static boolean fleetEnabled() {
        try {
            Object v = readFleetConfig().get("enabled");
            if (v instanceof Boolean b) return b;
            if (v != null) return Boolean.parseBoolean(String.valueOf(v));
        } catch (Exception ignored) {}
        return true;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> readStatusConfig() {
        try {
            Path p = configPath();
            if (!Files.exists(p)) return null;
            String text = Files.readString(p);
            Object parsed = GSON.fromJson(text, Map.class);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (e.getKey() instanceof String k) out.put(k, e.getValue());
                }
                return out;
            }
        } catch (Exception e) {
            System.out.println("[Fleet] cannot read StatusMod config: " + e.getMessage());
        }
        return null;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    static void pull() {
        if (!fleetEnabled()) {
            lastPullInfo = "disabled (fleet config)";
            return;
        }
        Map<String, Object> fleet = readFleetConfig();
        Map<String, Object> local = readStatusConfig();
        if (local == null) return;
        // apiKey is per-server identity: always from StatusMod's config, never stored in fleet file.
        String apiKey = str(local.get("apiKey"));
        if (apiKey.isEmpty()) return;
        // dashboardUrl: fleet file wins when set, else StatusMod's config.
        String dashboardUrl = trimSlash(str(fleet.get("dashboardUrl")));
        if (dashboardUrl.isEmpty()) dashboardUrl = trimSlash(str(local.get("dashboardUrl")));
        if (dashboardUrl.isEmpty() || apiKey.isEmpty()) return;
        if (!isSecureHttpUrl(dashboardUrl)) return;

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(dashboardUrl + "/api/fleet/config"))
                    .timeout(Duration.ofSeconds(20))
                    .header("x-api-key", apiKey)
                    .GET()
                    .build();
        } catch (Exception e) {
            lastPullInfo = "ex: bad url";
            return;
        }

        String body;
        try {
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                lastPullInfo = "HTTP " + response.statusCode();
                try { response.body().close(); } catch (Exception ignored) {}
                return;
            }
            try (InputStream in = response.body()) {
                body = readCapped(in, MAX_BODY_BYTES);
            }
        } catch (java.io.IOException | InterruptedException e) {
            lastPullInfo = "ex: " + e.getMessage();
            return;
        }
        lastPullInfo = "ok";

        JsonObject json;
        try {
            json = JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            return;
        }
        String fleetUrl = json.has("dashboard_url") && !json.get("dashboard_url").isJsonNull()
                ? json.get("dashboard_url").getAsString().trim() : "";
        String fleetSecret = json.has("setup_secret") && !json.get("setup_secret").isJsonNull()
                ? json.get("setup_secret").getAsString().trim() : "";

        boolean changed = false;
        StringBuilder what = new StringBuilder();
        if (!fleetSecret.isEmpty() && fleetSecret.length() <= 512 && !fleetSecret.equals(str(fleet.get("setupSecret")))) {
            fleet.put("setupSecret", fleetSecret);
            changed = true;
            what.append("setupSecret ");
        }
        if (!fleetUrl.isEmpty() && fleetUrl.length() <= 256 && !fleetUrl.equals(str(fleet.get("dashboardUrl")))) {
            if (isSecureHttpUrl(trimSlash(fleetUrl))) {
                fleet.put("dashboardUrl", trimSlash(fleetUrl));
                changed = true;
                what.append("dashboardUrl ");
            }
        }
        if (!changed) return;
        try {
            writeFleetConfig(fleet);
            lastChangeInfo = "applied " + what.toString().trim() + " (restart to take full effect)";
            System.out.println("[Fleet] applied fleet config: " + what.toString().trim()
                    + ". Restart the server to take full effect.");
        } catch (Exception e) {
            System.out.println("[Fleet] cannot write fleet config: " + e.getMessage());
        }
    }

    static String trimSlash(String url) {
        if (url == null) return "";
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    static boolean isSecureHttpUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (scheme.equals("https")) return true;
            if (!scheme.equals("http")) return false;
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.equals("localhost")) return true;
            if (host.startsWith("127.")) {
                for (String part : host.split("\\.", -1)) {
                    if (part.isEmpty()) return false;
                    try {
                        int n = Integer.parseInt(part);
                        if (n < 0 || n > 255) return false;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
                return true;
            }
            return host.equals("[::1]") || host.equals("::1");
        } catch (Exception ignored) {
            return false;
        }
    }

    static String readCapped(InputStream in, int maxBytes) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, 65536));
        byte[] buf = new byte[8192];
        int total = 0;
        int r;
        while ((r = in.read(buf)) != -1) {
            if (total + r > maxBytes) {
                out.write(buf, 0, maxBytes - total);
                total = maxBytes;
                break;
            }
            out.write(buf, 0, r);
            total += r;
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
