package com.hivemq.platform.demo.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrchestratorListEnvelopeDto(
        @JsonProperty("success") boolean success,
        @JsonProperty("data") OrchestratorsResponseDto data) {}
