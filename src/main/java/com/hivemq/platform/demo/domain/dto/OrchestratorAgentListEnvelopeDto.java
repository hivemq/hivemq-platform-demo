package com.hivemq.platform.demo.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OrchestratorAgentListEnvelopeDto(
        @JsonProperty("success") boolean success,
        @JsonProperty("data") List<OrchestratorAgentDto> data) {

    public List<OrchestratorAgentDto> dataOrEmpty() {
        return data != null ? data : List.of();
    }
}
