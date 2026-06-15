package com.hivemq.platform.demo.domain.dto;

import static com.hivemq.platform.demo.constants.Constants.Jwt.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.hivemq.platform.demo.config.Configuration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public record JwtClaimsDto(String orgId, String email, String pulseBaseUrl, String agentxBaseUrl) {

    public static JwtClaimsDto from(final JsonNode claims, Configuration.Fallback fallback) {

        final var orgs = claims.path(ORGS);

        if (!orgs.isArray() || orgs.isEmpty())
            throw new IllegalStateException("Access token has no orgs[]; cannot derive the context.");

        final var org = orgs.get(0);
        final var pulseResolvedUrl = org.path(PULSE).path(SERVER_URL).asText("");
        final var agentxResolvedUrl = org.path(AGENTX).path(SERVER_URL).asText("");

        final var orgId = coalesce(org.path(ID).asText(""), fallback.orgId());
        final var email = claims.path(EMAIL).asText("");
        final var pulseUrl = withScheme(coalesce(pulseResolvedUrl, fallback.pulseBaseUrl()));
        final var agentxUrl = withScheme(coalesce(agentxResolvedUrl, fallback.agentxBaseUrl()));

        log.info("OrgId: ({}), Email: ({}), PulseUrl: ({}), AgentxUrl: ({})", orgId, email, pulseUrl, agentxUrl);

        return new JwtClaimsDto(orgId, email, pulseUrl, agentxUrl);
    }

    private static String coalesce(final String value, final String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String withScheme(final String url) {
        // claims carry a bare host (e.g. "pulse2.dev.hmqc.dev"); OkHttp/Retrofit require a scheme
        if (url == null || url.isBlank() || url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return "https://" + url;
    }
}
