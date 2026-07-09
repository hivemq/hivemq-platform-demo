package com.hivemq.platform.demo.mqtt;

import java.util.List;

public record SensorProfile(String name, double mean, double stddev, String unit) {

    public static final List<SensorProfile> DEFAULTS = List.of(
            new SensorProfile("temperature", 22.0, 1.0, "C"),
            new SensorProfile("pressure", 101.3, 0.5, "kPa"),
            new SensorProfile("vibration", 0.5, 0.08, "mm/s"));
}
