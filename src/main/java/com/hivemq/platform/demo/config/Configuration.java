package com.hivemq.platform.demo.config;

public record Configuration(Auth0 auth0, Fallback fallback) {
    public record Auth0(String domain, String clientId, String audience, String scope) {}

    public record Fallback(String orgId, String pulseBaseUrl, String agentxBaseUrl) {}
}
