package com.hivemq.platform.demo.domain.dto;

public record SensorReadingDto(double value, String unit, long ts) {}
