package com.hivemq.platform.demo.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EnrollmentTokenDto(
        @JsonProperty("id") String id,
        @JsonProperty("plaintextToken") String plaintextToken,
        @JsonProperty("expiresAt") String expiresAt,
        @JsonProperty("dockerRunCommand") String dockerRunCommand) {}
