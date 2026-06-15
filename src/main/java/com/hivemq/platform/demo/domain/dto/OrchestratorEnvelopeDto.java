package com.hivemq.platform.demo.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrchestratorEnvelopeDto(
        @JsonProperty("success") boolean success,
        @JsonProperty("data") OrchestratorDto data) {}
