package com.hivemq.platform.demo.containers;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ContainerSpec(
        String name,
        String image,
        String networkName,
        List<String> networkAliases,
        Map<String, String> env,
        List<PortBinding> ports,
        List<Mount> mounts,
        boolean restartUnlessStopped,
        HealthSpec health) {

    public record HealthSpec(
            List<String> test, Duration interval, Duration timeout, int retries, Duration startPeriod) {}

    public ContainerSpec withMergedEnv(final Map<String, String> dynamic) {
        final var merged = new LinkedHashMap<>(env);
        merged.putAll(dynamic);
        return new ContainerSpec(
                name,
                image,
                networkName,
                networkAliases,
                Map.copyOf(merged),
                ports,
                mounts,
                restartUnlessStopped,
                health);
    }
}
