package com.hivemq.platform.demo.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record CreateOrchestratorAgentRequestDto(
        @JsonProperty("templateId") String templateId,
        @JsonProperty("version") String version,
        @JsonProperty("environment") Map<String, String> environment) {}
