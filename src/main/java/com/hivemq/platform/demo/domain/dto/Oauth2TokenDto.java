package com.hivemq.platform.demo.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Oauth2TokenDto(
        String accessToken,
        String idToken,
        String refreshToken,
        @JsonProperty("expires_in") long expiresInSeconds,
        String scope,
        String tokenType) {}
