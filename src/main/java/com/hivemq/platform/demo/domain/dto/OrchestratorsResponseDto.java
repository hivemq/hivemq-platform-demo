package com.hivemq.platform.demo.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OrchestratorsResponseDto(
        @JsonProperty("items") List<OrchestratorDto> items) {

    public List<OrchestratorDto> itemsOrEmpty() {
        return items != null ? items : List.of();
    }
}
