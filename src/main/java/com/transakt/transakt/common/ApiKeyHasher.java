package com.transakt.transakt.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;


public final class ApiKeyHasher {

    public static final int PREFIX_LENGTH = 8;

    private ApiKeyHasher() {
    }

    public static String prefixOf(String apiKey) {
        if (apiKey == null || apiKey.length() < PREFIX_LENGTH) {
            return null;
        }
        return apiKey.substring(0, PREFIX_LENGTH);
    }

    public static String hash(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static boolean matches(String apiKey, String storedHash) {
        if (apiKey == null || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(apiKey).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }
}