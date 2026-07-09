package com.hivemq.platform.demo.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EnrollmentTokenEnvelopeDto(
        @JsonProperty("success") boolean success,
        @JsonProperty("data") EnrollmentTokenDto data) {}
