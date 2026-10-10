package com.teufel.statusmod.util;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Locale;

public final class CodeGenerator {
    public static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RNG = new SecureRandom();
    /** Max HTTP response body accepted (sync poll, transfer, setup, code). */
    public static final int MAX_BODY_BYTES = 8 * 1024 * 1024;

    private CodeGenerator() {}

    public static String generate(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    public static String trimTrailingSlash(String url) {
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * Strict dashboard URL check. Plain prefix checks ("http://localhost…")
     * also match lookalike hosts (localhost.evil.com, 127.evil.com) and would
     * leak the apiKey via header to an attacker. Parse the host instead.
     */
    public static boolean isSecureHttpUrl(String url) {
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

    /** Reads at most maxBytes from a response stream (DoS cap for huge bodies). */
    public static String readCapped(InputStream in, int maxBytes) throws java.io.IOException {
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
        return out.toString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
