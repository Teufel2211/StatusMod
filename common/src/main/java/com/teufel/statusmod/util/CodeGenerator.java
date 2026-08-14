package com.teufel.statusmod.util;

import java.security.SecureRandom;

public final class CodeGenerator {
    public static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RNG = new SecureRandom();

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
}
