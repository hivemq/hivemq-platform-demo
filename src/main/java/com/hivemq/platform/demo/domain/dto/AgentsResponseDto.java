package com.hivemq.platform.demo.domain.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AgentsResponseDto(
        @JsonProperty("items") @JsonAlias({"agents", "data"})
        List<AgentDto> items) {

    public List<AgentDto> itemsOrEmpty() {
        return items != null ? items : List.of();
    }
}
