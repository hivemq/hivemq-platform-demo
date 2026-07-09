package com.hivemq.platform.demo.oauth2;

import static com.hivemq.platform.demo.utils.JwtUtils.decodeClaims;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.platform.demo.config.Configuration;
import com.hivemq.platform.demo.domain.dto.JwtClaimsDto;
import com.hivemq.platform.demo.domain.dto.Oauth2TokenDto;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;

public class SessionManager {

    private static final long EXPIRY_SKEW_SECONDS = 30;

    private final Auth0Client auth0Client;
    private final ObjectMapper objectMapper;
    private final Configuration.Fallback fallback;

    private volatile Instant expiresAt;
    private volatile JwtClaimsDto claims;
    private volatile Oauth2TokenDto token;

    public SessionManager(
            final Oauth2TokenDto token,
            final Auth0Client auth0Client,
            final ObjectMapper objectMapper,
            final Configuration.Fallback fallback) {
        this.auth0Client = auth0Client;
        this.objectMapper = objectMapper;
        this.fallback = fallback;
        set(token);
    }

    public JwtClaimsDto claims() {
        return claims;
    }

    public Oauth2TokenDto token() {
        if (!isStale()) {
            return token;
        }
        synchronized (this) {
            if (isStale()) {
                set(refresh());
            }
            return token;
        }
    }

    private boolean isStale() {
        return !Instant.now().isBefore(expiresAt);
    }

    private Oauth2TokenDto refresh() {
        try {
            return auth0Client.refreshToken(token.refreshToken());
        } catch (final IOException cause) {
            throw new UncheckedIOException("Failed to refresh the access token", cause);
        }
    }

    private void set(final Oauth2TokenDto refreshed) {
        this.token = refreshed;
        this.claims = JwtClaimsDto.from(decodeClaims(objectMapper, this.token.accessToken()), fallback);
        this.expiresAt = Instant.now().plusSeconds(refreshed.expiresInSeconds() - EXPIRY_SKEW_SECONDS);
    }
}
