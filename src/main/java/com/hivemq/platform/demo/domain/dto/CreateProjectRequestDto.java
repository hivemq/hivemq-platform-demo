package com.hivemq.platform.demo.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateProjectRequestDto(
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("organizationId") String organizationId) {}
