package com.hivemq.platform.demo.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateOrchestratorRequestDto(
        @JsonProperty("name") String name,
        @JsonProperty("type") String type,
        @JsonProperty("communicationType") String communicationType,
        @JsonProperty("networkId") String networkId) {}
