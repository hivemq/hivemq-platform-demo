package com.hivemq.platform.demo.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NetworkListEnvelopeDto(
        @JsonProperty("success") boolean success,
        @JsonProperty("data") NetworksResponseDto data) {}
