package com.hivemq.platform.demo.domain.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.platform.demo.config.Configuration;
import org.junit.jupiter.api.Test;

class JwtClaimsDtoTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Configuration.Fallback FALLBACK =
            new Configuration.Fallback("dummy", "https://pulse.fallback", "https://act.hivemq.com", "https://console");

    private static JsonNode orgs(final String json) {
        try {
            return MAPPER.readTree(json).path("orgs");
        } catch (final Exception cause) {
            throw new RuntimeException(cause);
        }
    }

    private static String orgId(final JsonNode org) {
        return org.path("id").asText("");
    }

    @Test
    void selectsTheSinglePulseOrg() {
        final var orgs = orgs("{\"orgs\":[{\"id\":\"a\",\"pulse\":{\"serverUrl\":\"p\"}}]}");
        assertEquals("a", orgId(JwtClaimsDto.selectOrg(orgs, null)));
    }

    @Test
    void skipsOrgsWithoutPulse() {
        final var orgs = orgs("{\"orgs\":[{\"id\":\"a\"},{\"id\":\"b\",\"pulse\":{\"serverUrl\":\"p\"}}]}");
        assertEquals("b", orgId(JwtClaimsDto.selectOrg(orgs, null)));
    }

    @Test
    void picksTheFirstPulseOrgWhenSeveralAndNoPin() {
        final var orgs = orgs("{\"orgs\":["
                + "{\"id\":\"a\",\"pulse\":{\"serverUrl\":\"p\"}},"
                + "{\"id\":\"b\",\"pulse\":{\"serverUrl\":\"q\"}}]}");
        assertEquals("a", orgId(JwtClaimsDto.selectOrg(orgs, null)));
    }

    @Test
    void honoursPinnedOrgIdMatchingAPulseOrg() {
        final var orgs = orgs("{\"orgs\":["
                + "{\"id\":\"a\",\"pulse\":{\"serverUrl\":\"p\"}},"
                + "{\"id\":\"b\",\"pulse\":{\"serverUrl\":\"q\"}}]}");
        assertEquals("b", orgId(JwtClaimsDto.selectOrg(orgs, "b")));
    }

    @Test
    void rejectsPinnedOrgIdThatIsNotPulseEnabled() {
        final var orgs = orgs("{\"orgs\":[" + "{\"id\":\"a\",\"pulse\":{\"serverUrl\":\"p\"}}," + "{\"id\":\"b\"}]}");
        final var ex = assertThrows(IllegalStateException.class, () -> JwtClaimsDto.selectOrg(orgs, "b"));
        assertTrue(ex.getMessage().contains("HIVEMQ_ORG_ID"), ex.getMessage());
        assertTrue(ex.getMessage().contains("[a]"), ex.getMessage());
    }

    @Test
    void rejectsPinnedOrgIdThatIsUnknown() {
        final var orgs = orgs("{\"orgs\":[{\"id\":\"a\",\"pulse\":{\"serverUrl\":\"p\"}}]}");
        assertThrows(IllegalStateException.class, () -> JwtClaimsDto.selectOrg(orgs, "zzz"));
    }

    @Test
    void failsWhenNoOrgHasPulse() {
        final var orgs = orgs("{\"orgs\":[{\"id\":\"a\"},{\"id\":\"b\"}]}");
        assertThrows(IllegalStateException.class, () -> JwtClaimsDto.selectOrg(orgs, null));
    }

    @Test
    void fromFailsOnEmptyOrgsArray() {
        try {
            final var claims = MAPPER.readTree("{\"orgs\":[]}");
            assertThrows(IllegalStateException.class, () -> JwtClaimsDto.from(claims, FALLBACK));
        } catch (final Exception cause) {
            throw new RuntimeException(cause);
        }
    }
}
