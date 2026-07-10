package com.hivemq.platform.demo.mqtt;

import java.util.List;

public record SensorProfile(String name, double mean, double stddev, String unit) {

    // stddev is kept small relative to mean (coefficient of variation ≤ ~5%) so normal noise stays
    // well inside the evaluator's ±20% outlier band — only the injected anomalies trip the rule, not
    // baseline jitter. Vibration in particular was 0.08 (16% CV), which blew past ±20% on pure noise
    // and produced constant false positives; 0.02 (4% CV) brings it in line with the other two.
    public static final List<SensorProfile> DEFAULTS = List.of(
            new SensorProfile("temperature", 22.0, 1.0, "C"),
            new SensorProfile("pressure", 101.3, 0.5, "kPa"),
            new SensorProfile("vibration", 0.5, 0.02, "mm/s"));
}
