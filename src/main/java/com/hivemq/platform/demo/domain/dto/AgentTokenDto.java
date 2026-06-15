package com.hivemq.platform.demo.domain.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AgentTokenDto(
        @JsonProperty("token") @JsonAlias({"activationToken", "connectionString", "value", "jwt"})
        String token) {}
