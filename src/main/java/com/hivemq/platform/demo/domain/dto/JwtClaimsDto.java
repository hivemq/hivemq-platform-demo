package com.hivemq.platform.demo.domain.dto;

import static com.hivemq.platform.demo.constants.Constants.Jwt.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.hivemq.platform.demo.config.Configuration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public record JwtClaimsDto(String orgId, String email, String pulseBaseUrl, String agentxBaseUrl) {

    public static JwtClaimsDto from(final JsonNode claims, Configuration.Fallback fallback) {

        final var orgs = claims.path(ORGS);

        if (!orgs.isArray() || orgs.isEmpty())
            throw new IllegalStateException("Access token has no orgs[]; cannot derive the context.");

        // only pulse-enabled orgs can run the demo; HIVEMQ_ORG_ID pins the choice when there are several
        final var org = selectOrg(orgs, System.getenv(HIVEMQ_ORG_ID_ENV));
        final var resolvedOrgId = org.path(ID).asText("");
        final var resolvedEmail = claims.path(EMAIL).asText("");
        final var resolvedPulseUrl = org.path(PULSE).path(SERVER_URL).asText("");
        final var resolvedAgentxUrl = org.path(AGENTX).path(SERVER_URL).asText("");

        log.info(
                "Claimed OrgId: ({}), Claimed Email: ({}), Claimed PulseUrl: ({}), Claimed AgentxUrl: ({})",
                resolvedOrgId,
                resolvedEmail,
                resolvedPulseUrl,
                resolvedAgentxUrl);

        final var orgId = coalesce(resolvedOrgId, fallback.orgId());
        final var pulseUrl = withScheme(coalesce(resolvedPulseUrl, fallback.pulseBaseUrl()));
        final var agentxUrl = withScheme(coalesce(resolvedAgentxUrl, fallback.agentxBaseUrl()));

        log.info(
                "OrgId: ({}), Email: ({}), PulseUrl: ({}), AgentxUrl: ({})", orgId, resolvedEmail, pulseUrl, agentxUrl);

        return new JwtClaimsDto(orgId, resolvedEmail, pulseUrl, agentxUrl);
    }

    /**
     * Selects the org whose context the demo will run against. Only orgs carrying a pulse configuration are
     * eligible. When {@code pinnedOrgId} is set it must match an eligible org; otherwise the first eligible
     * org is used.
     */
    static JsonNode selectOrg(final JsonNode orgs, final String pinnedOrgId) {

        final var pulseOrgs = new ArrayList<JsonNode>();
        for (final var org : orgs) {
            if (hasPulse(org)) {
                pulseOrgs.add(org);
            }
        }

        if (pulseOrgs.isEmpty()) {
            throw new IllegalStateException(
                    "No org in the access token has a pulse configuration; the demo cannot run.");
        }

        if (pinnedOrgId != null && !pinnedOrgId.isBlank()) {
            return pulseOrgs.stream()
                    .filter(org -> pinnedOrgId.equals(org.path(ID).asText("")))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(String.format(
                            "%s=%s does not match any pulse-enabled org. Pulse-enabled orgs: %s",
                            HIVEMQ_ORG_ID_ENV, pinnedOrgId, pulseOrgIds(pulseOrgs))));
        }

        return pulseOrgs.get(0);
    }

    private static boolean hasPulse(final JsonNode org) {
        return !org.path(PULSE).path(SERVER_URL).asText("").isBlank();
    }

    private static List<String> pulseOrgIds(final List<JsonNode> pulseOrgs) {
        return pulseOrgs.stream().map(org -> org.path(ID).asText("")).toList();
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
