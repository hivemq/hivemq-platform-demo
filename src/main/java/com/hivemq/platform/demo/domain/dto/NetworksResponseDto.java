package com.hivemq.platform.demo.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record NetworksResponseDto(@JsonProperty("items") List<NetworkDto> items) {

    public List<NetworkDto> itemsOrEmpty() {
        return items != null ? items : List.of();
    }
}
