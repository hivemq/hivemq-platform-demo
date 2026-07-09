package com.hivemq.platform.demo.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserConfigDto(@JsonProperty("sendGridKey") String sendGridKey) {}
