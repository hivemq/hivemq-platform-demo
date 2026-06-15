package com.hivemq.platform.demo.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrchestratorAgentEnvelopeDto(
        @JsonProperty("status") String status,
        @JsonProperty("data") OrchestratorAgentDto data) {}
