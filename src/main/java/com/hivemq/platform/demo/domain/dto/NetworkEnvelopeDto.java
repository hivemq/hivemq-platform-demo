package com.hivemq.platform.demo.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NetworkEnvelopeDto(
        @JsonProperty("success") boolean success,
        @JsonProperty("data") NetworkDto data) {}
