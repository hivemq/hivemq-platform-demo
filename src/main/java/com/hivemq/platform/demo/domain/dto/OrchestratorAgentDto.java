package com.hivemq.platform.demo.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrchestratorAgentDto(
        @JsonProperty("agentId") String id,
        @JsonProperty("name") String name,
        @JsonProperty("agentRole") String agentRole,
        @JsonProperty("templateId") String templateId,
        @JsonProperty("version") String version,
        @JsonProperty("orchestratorId") String orchestratorId,
        @JsonProperty("networkId") String networkId,
        @JsonProperty("status") String status,
        @JsonProperty("health") String health) {}
