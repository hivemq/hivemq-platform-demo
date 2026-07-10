package com.hivemq.platform.demo.containers;

import static com.hivemq.platform.demo.constants.Constants.Containers.HEALTH_STATUS_HEALTHY;
import static io.reactivex.rxjava3.core.Completable.fromAction;
import static io.reactivex.rxjava3.core.Single.fromCallable;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.*;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DockerManager {

    private final DockerClient dockerClient;
    private final Scheduler ioScheduler;

    public Completable ensureDockerAvailable() {
        return fromAction(() -> {
                    try {
                        dockerClient.pingCmd().exec();
                    } catch (final RuntimeException cause) {
                        throw new IllegalStateException(
                                "Docker is not available — make sure Docker is installed and running, then retry"
                                        + " (could not reach the Docker daemon: " + rootCauseMessage(cause) + ")");
                    }
                })
                .subscribeOn(ioScheduler);
    }

    private static String rootCauseMessage(final Throwable error) {
        var cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        final var message = cause.getMessage();
        return message != null ? message : cause.getClass().getSimpleName();
    }

    public Completable ensureNetwork(final String name) {
        return fromAction(() -> {
                    final var exists = dockerClient.listNetworksCmd().withNameFilter(name).exec().stream()
                            .anyMatch(network -> name.equals(network.getName()));
                    if (!exists) {
                        try {
                            dockerClient
                                    .createNetworkCmd()
                                    .withName(name)
                                    .withDriver("bridge")
                                    .exec();
                        } catch (final ConflictException ignored) {
                            // created concurrently between the check and create — fine
                        }
                    }
                })
                .subscribeOn(ioScheduler);
    }

    public Single<String> buildImage(final String resourceDir, final List<String> files, final String tag) {
        return fromCallable(() -> {
                    final var context = Files.createTempDirectory("broker-build-");
                    try {
                        for (final var file : files) {
                            try (var in = DockerManager.class.getResourceAsStream("/" + resourceDir + "/" + file)) {
                                if (in == null) {
                                    throw new IllegalStateException(
                                            "Missing build resource: /" + resourceDir + "/" + file);
                                }
                                Files.copy(in, context.resolve(file));
                            }
                        }
                        return dockerClient
                                .buildImageCmd()
                                .withBaseDirectory(context.toFile())
                                .withDockerfile(context.resolve("Dockerfile").toFile())
                                .withTags(Set.of(tag))
                                .withPull(true)
                                .exec(new BuildImageResultCallback())
                                .awaitImageId();
                    } finally {
                        deleteRecursively(context);
                    }
                })
                .subscribeOn(ioScheduler);
    }

    private static void deleteRecursively(final Path dir) {
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (final IOException ignored) {
                    // best-effort temp cleanup
                }
            });
        } catch (final IOException ignored) {
            // best-effort temp cleanup
        }
    }

    public Completable pullImage(final String ref) {
        return fromAction(() -> dockerClient
                        .pullImageCmd(ref)
                        .exec(new PullImageResultCallback())
                        .awaitCompletion())
                .subscribeOn(ioScheduler);
    }

    public Completable forceRemoveByName(final String name) {
        return fromAction(() -> {
                    try {
                        dockerClient.removeContainerCmd(name).withForce(true).exec();
                    } catch (final NotFoundException ignored) {
                        // nothing to remove — recreate is idempotent
                    }
                })
                .subscribeOn(ioScheduler);
    }

    public Single<String> createAndStart(final ContainerSpec spec) {
        return fromCallable(() -> {
                    final var exposedPorts = new ArrayList<ExposedPort>();
                    final var portBindings = new Ports();
                    for (final var port : spec.ports()) {
                        final var exposed = ExposedPort.tcp(port.containerPort());
                        exposedPorts.add(exposed);
                        portBindings.bind(exposed, Ports.Binding.bindPort(port.hostPort()));
                    }

                    final var binds = spec.mounts().stream()
                            .map(mount -> new Bind(
                                    mount.hostPath(),
                                    new Volume(mount.containerPath()),
                                    mount.readOnly() ? AccessMode.ro : AccessMode.rw))
                            .toList();

                    final var hostConfig = HostConfig.newHostConfig()
                            .withNetworkMode(spec.networkName())
                            .withPortBindings(portBindings)
                            .withBinds(binds)
                            .withSecurityOpts(List.of("label=disable"));

                    if (spec.restartUnlessStopped()) {
                        hostConfig.withRestartPolicy(RestartPolicy.unlessStoppedRestart());
                    }

                    final var create = dockerClient
                            .createContainerCmd(spec.image())
                            .withName(spec.name())
                            .withUser("root")
                            .withEnv(toEnvList(spec.env()))
                            .withExposedPorts(exposedPorts)
                            .withHostConfig(hostConfig);

                    if (!spec.networkAliases().isEmpty()) {
                        create.withAliases(spec.networkAliases());
                    }

                    final var health = spec.health();
                    if (health != null) {
                        create.withHealthcheck(new HealthCheck()
                                .withTest(health.test())
                                .withInterval(health.interval().toNanos())
                                .withTimeout(health.timeout().toNanos())
                                .withStartPeriod(health.startPeriod().toNanos())
                                .withRetries(health.retries()));
                    }

                    final var id = create.exec().getId();
                    dockerClient.startContainerCmd(id).exec();
                    return id;
                })
                .subscribeOn(ioScheduler);
    }

    public Completable waitUntilHealthy(final String name, final Duration pollInterval, final Duration timeout) {
        return fromCallable(() -> dockerClient
                        .inspectContainerCmd(name)
                        .exec()
                        .getState()
                        .getHealth()
                        .getStatus())
                .subscribeOn(ioScheduler)
                .repeatWhen(completed -> completed.delay(pollInterval.toMillis(), MILLISECONDS, ioScheduler))
                .takeUntil((String status) -> HEALTH_STATUS_HEALTHY.equals(status))
                .filter(HEALTH_STATUS_HEALTHY::equals)
                .firstOrError()
                .timeout(timeout.toMillis(), MILLISECONDS, ioScheduler)
                .ignoreElement();
    }

    private static List<String> toEnvList(final Map<String, String> env) {
        return env.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList();
    }
}
