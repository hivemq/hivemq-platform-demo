package com.hivemq.platform.demo.containers;

import static com.hivemq.platform.demo.constants.Constants.Containers.*;
import static io.reactivex.rxjava3.core.Completable.fromAction;
import static io.reactivex.rxjava3.core.Completable.mergeArray;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Scheduler;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ContainersRunner {

    private final Scheduler ioScheduler;
    private final DockerManager dockerManager;

    public Completable run(final Map<String, String> brokerEnv, final Map<String, String> orchestratorEnv) {

        final var broker = brokerSpec().withMergedEnv(brokerEnv);
        final var orchestrator = orchestratorSpec().withMergedEnv(orchestratorEnv);

        // Phase 1 — network + both images, all independent, in parallel.
        final var prepare = mergeArray(
                logStep("Ensuring Docker network '" + NETWORK_NAME + "' …")
                        .andThen(dockerManager.ensureNetwork(NETWORK_NAME)),
                dockerManager
                        .buildImage(BROKER_BUILD_RESOURCE_DIR, BROKER_BUILD_FILES, BROKER_IMAGE_TAG)
                        .ignoreElement()
                        .doOnComplete(() -> log.info("Broker image built.")),
                dockerManager.pullImage(ORCHESTRATOR_IMAGE).doOnComplete(() -> log.info("Orchestrator image pulled.")));

        // Phase 2 — start both containers and gate on their healthchecks, in parallel. Each
        // DockerManager op carries its own subscribeOn(ioScheduler), so the two arms run on
        // separate virtual threads; mergeArray completes when both are healthy and fails fast.
        final var startContainers = mergeArray(startAndAwaitHealthy(broker), startAndAwaitHealthy(orchestrator));

        return logStep("Preparing network + images (first run is slow) …")
                .andThen(prepare)
                .andThen(logStep("Starting broker + orchestrator containers …"))
                .andThen(startContainers)
                .andThen(logStep("Both containers are up and healthy."))
                .subscribeOn(ioScheduler);
    }

    private Completable startAndAwaitHealthy(final ContainerSpec spec) {
        return recreate(spec)
                .andThen(dockerManager.waitUntilHealthy(spec.name(), HEALTH_INTERVAL, HEALTH_TIMEOUT))
                .doOnComplete(() -> log.info("Container '{}' is healthy.", spec.name()));
    }

    public Completable teardown() {
        return mergeArray(
                        dockerManager.forceRemoveByName(BROKER_CONTAINER_NAME),
                        dockerManager.forceRemoveByName(ORCHESTRATOR_CONTAINER_NAME))
                .subscribeOn(ioScheduler);
    }

    private Completable recreate(final ContainerSpec spec) {
        return dockerManager
                .forceRemoveByName(spec.name())
                .andThen(dockerManager.createAndStart(spec).ignoreElement());
    }

    private static Completable logStep(final String message) {
        return fromAction(() -> log.info(message));
    }

    private ContainerSpec brokerSpec() {
        return new ContainerSpec(
                BROKER_CONTAINER_NAME,
                BROKER_IMAGE_TAG,
                NETWORK_NAME,
                List.of(BROKER_NETWORK_ALIAS),
                Map.of(),
                List.of(
                        new PortBinding(BROKER_MQTT_PORT, BROKER_MQTT_PORT),
                        new PortBinding(BROKER_CONTROL_CENTER_PORT, BROKER_CONTROL_CENTER_PORT)),
                List.of(),
                false,
                new ContainerSpec.HealthSpec(
                        BROKER_HEALTHCHECK_TEST,
                        BROKER_HEALTHCHECK_INTERVAL,
                        BROKER_HEALTHCHECK_TIMEOUT,
                        BROKER_HEALTHCHECK_RETRIES,
                        BROKER_HEALTHCHECK_START_PERIOD));
    }

    private ContainerSpec orchestratorSpec() {
        return new ContainerSpec(
                ORCHESTRATOR_CONTAINER_NAME,
                ORCHESTRATOR_IMAGE,
                NETWORK_NAME,
                List.of(),
                Map.of(
                        ENV_CONTROL_PLANE_URL, CONTROL_PLANE_URL,
                        ENV_AGENT_BUS_BROKER_URL, AGENT_BUS_BROKER_URL),
                List.of(),
                List.of(new Mount(DOCKER_SOCK, DOCKER_SOCK, false)),
                false,
                new ContainerSpec.HealthSpec(
                        ORCHESTRATOR_HEALTHCHECK_TEST,
                        ORCHESTRATOR_HEALTHCHECK_INTERVAL,
                        ORCHESTRATOR_HEALTHCHECK_TIMEOUT,
                        ORCHESTRATOR_HEALTHCHECK_RETRIES,
                        ORCHESTRATOR_HEALTHCHECK_START_PERIOD));
    }
}
