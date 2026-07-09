package com.hivemq.platform.demo.domain.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AgentDto(
        @JsonProperty("id") @JsonAlias({"agentId", "uuid"}) String id,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,

        @JsonProperty("infrastructureType") @JsonAlias({"type"})
        String infrastructureType) {}
