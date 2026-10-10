package com.teufel.fleet;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class FleetCommand {
    private FleetCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fleet")
            .then(Commands.literal("status").executes(ctx -> {
                showStatus(ctx.getSource());
                return 1;
            }))
            .then(Commands.literal("apply").executes(ctx -> {
                if (!FleetPerm.isAdmin(ctx.getSource())) {
                    ctx.getSource().sendFailure(Component.literal("Du hast nicht genügend Rechte."));
                    return 0;
                }
                FleetManager.requestSync();
                ctx.getSource().sendSuccess(() -> Component.literal("Fleet-Sync angestoßen (Pull in wenigen Sekunden)."), false);
                return 1;
            }))
            .then(Commands.literal("owner-code").executes(ctx -> {
                ownerCode(ctx.getSource());
                return 1;
            })));
    }

    private static void showStatus(CommandSourceStack src) {
        if (!FleetPerm.isAdmin(src)) {
            src.sendFailure(Component.literal("Du hast nicht genügend Rechte."));
            return;
        }
        src.sendSuccess(() -> Component.literal("Fleet Sync (build " + FleetMod.BUILD + "):"), false);
        src.sendSuccess(() -> Component.literal(" letzter Pull: " + FleetManager.lastPullInfo), false);
        src.sendSuccess(() -> Component.literal(" letzte Änderung: " + FleetManager.lastChangeInfo), false);
        src.sendSuccess(() -> Component.literal("Intervall: 60s + /fleet apply. serverId/apiKey bleiben je Server erhalten."), false);
    }

    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final java.security.SecureRandom CODE_RNG = new java.security.SecureRandom();

    private static String generateCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CODE_ALPHABET.charAt(CODE_RNG.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * Issues an owner one-time login code (admin only). The code is printed to
     * the SERVER CONSOLE ONLY, never to chat. Redeem it via the "Log in as
     * owner" button on the dashboard login page (lands on the fleet admin
     * page). Valid 10 minutes, single use.
     */
    private static void ownerCode(CommandSourceStack src) {
        if (!FleetPerm.isAdmin(src)) {
            src.sendFailure(Component.literal("Du hast nicht genügend Rechte."));
            return;
        }
        java.util.Map<String, Object> local = FleetManager.readStatusConfig();
        if (local == null) {
            src.sendFailure(Component.literal("StatusMod-Config nicht gefunden."));
            return;
        }
        Object ak = local.get("apiKey");
        String apiKey = ak == null ? "" : String.valueOf(ak).trim();
        if (apiKey.isEmpty()) {
            src.sendFailure(Component.literal("Kein API-Key konfiguriert (Server zuerst claimen)."));
            return;
        }
        java.util.Map<String, Object> fleet = FleetManager.readFleetConfig();
        String baseUrl = FleetManager.trimSlash(strOf(fleet.get("dashboardUrl")));
        if (baseUrl.isEmpty()) baseUrl = FleetManager.trimSlash(strOf(local.get("dashboardUrl")));
        if (baseUrl.isEmpty() || !FleetManager.isSecureHttpUrl(baseUrl)) {
            src.sendFailure(Component.literal("Keine gültige Dashboard-URL konfiguriert."));
            return;
        }
        final String endpoint = baseUrl + "/api/auth/owner-code";
        final String code = generateCode(16);
        net.minecraft.server.MinecraftServer server;
        try {
            server = src.getServer();
        } catch (Exception e) {
            server = null;
        }
        final net.minecraft.server.MinecraftServer fServer = server;
        src.sendSuccess(() -> Component.literal("Owner-Code wird erstellt..."), false);
        Thread t = new Thread(() -> {
            try {
                com.google.gson.JsonObject payload = new com.google.gson.JsonObject();
                payload.addProperty("code", code);
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(endpoint))
                        .timeout(java.time.Duration.ofSeconds(20))
                        .header("Content-Type", "application/json")
                        .header("x-api-key", apiKey)
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload.toString()))
                        .build();
                java.net.http.HttpResponse<java.io.InputStream> response =
                        java.net.http.HttpClient.newBuilder()
                                .connectTimeout(java.time.Duration.ofSeconds(10))
                                .build()
                                .send(request, java.net.http.HttpResponse.BodyHandlers.ofInputStream());
                int status;
                try (java.io.InputStream in = response.body()) {
                    status = response.statusCode();
                }
                if (status >= 200 && status < 300) {
                    System.out.println("==============================================");
                    System.out.println("[Fleet] OWNER LOGIN CODE: " + code);
                    System.out.println("[Fleet] Gueltig 10 Minuten, einmalig. Im Dashboard auf \"Log in as owner\" klicken und eingeben (Admin-Bereich mit Fleet-Einstellungen). Niemals teilen.");
                    System.out.println("==============================================");
                    reply(src, fServer, true, "Owner-Code in die Server-Konsole gedruckt (10 Min gültig).");
                } else if (status == 401 || status == 403) {
                    reply(src, fServer, false, "Owner-Code fehlgeschlagen (Auth-Fehler). Server-Claim/API-Key prüfen.");
                } else {
                    reply(src, fServer, false, "Owner-Code fehlgeschlagen (HTTP " + status + ").");
                }
            } catch (Throwable e) {
                reply(src, fServer, false, "Owner-Code fehlgeschlagen (" + e.getMessage() + ").");
            }
        }, "fleet-ownercode");
        t.setDaemon(true);
        t.start();
    }

    private static String strOf(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static void reply(CommandSourceStack src, net.minecraft.server.MinecraftServer server, boolean ok, String msg) {
        Runnable r = () -> {
            try {
                if (ok) {
                    src.sendSuccess(() -> Component.literal(msg), false);
                } else {
                    src.sendFailure(Component.literal(msg));
                }
            } catch (Throwable ignored) {}
        };
        try {
            if (server != null) {
                server.execute(r);
            } else {
                r.run();
            }
        } catch (Throwable ignored) {}
    }
}
