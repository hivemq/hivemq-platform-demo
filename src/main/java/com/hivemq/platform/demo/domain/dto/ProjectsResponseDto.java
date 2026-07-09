package com.hivemq.platform.demo.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ProjectsResponseDto(@JsonProperty("items") List<ProjectDto> items) {

    public List<ProjectDto> itemsOrEmpty() {
        return items != null ? items : List.of();
    }
}
