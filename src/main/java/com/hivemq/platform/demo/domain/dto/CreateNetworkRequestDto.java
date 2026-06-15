package com.hivemq.platform.demo.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateNetworkRequestDto(
        @JsonProperty("name") String name,
        @JsonProperty("description") String description) {}
