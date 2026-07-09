package com.hivemq.platform.demo.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateAgentRequestDto(
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("infrastructureType") String infrastructureType) {}
