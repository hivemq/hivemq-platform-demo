package com.hivemq.platform.demo.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class PkceUtils {

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String verifier() {
        return randomUrlSafe(32);
    }

    public static String state() {
        return randomUrlSafe(16);
    }

    public static String challenge(final String verifier) {
        try {
            final var digest =
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return BASE64_URL.encodeToString(digest);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String randomUrlSafe(final int bytes) {
        final var buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return BASE64_URL.encodeToString(buf);
    }
}
