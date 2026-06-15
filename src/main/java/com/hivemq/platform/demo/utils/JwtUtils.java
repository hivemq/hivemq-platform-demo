package com.hivemq.platform.demo.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class JwtUtils {

    public static JsonNode decodeClaims(final ObjectMapper mapper, final String jwt) {
        final var parts = jwt.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Malformed JWT");
        }
        final var payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        try {
            return mapper.readTree(payload);
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to parse JWT payload", e);
        }
    }
}
