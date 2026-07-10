package com.hivemq.platform.demo;

import static com.hivemq.platform.demo.constants.Constants.Containers.ENV_ORCHESTRATOR_ID;
import static com.hivemq.platform.demo.constants.Constants.Containers.ENV_ORCHESTRATOR_NAME;
import static com.hivemq.platform.demo.constants.Constants.Containers.ENV_PULSE_TOKEN;
import static com.hivemq.platform.demo.constants.Constants.Containers.ENV_REGISTRATION_TOKEN;
import static io.reactivex.rxjava3.core.Completable.fromAction;
import static io.reactivex.rxjava3.core.Completable.mergeArray;

import com.hivemq.platform.demo.di.component.DaggerApplicationComponent;
import com.hivemq.platform.demo.domain.dto.ArgsDto;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {

    static void main(String[] args) {

        final var applicationComponent = DaggerApplicationComponent.factory().create(new ArgsDto(args));

        final var progress = applicationComponent.consoleProgress();
        final var loopbackServer = applicationComponent.loopbackServer();
        final var containersRunner = applicationComponent.containersRunner();
        final var anomaliesDataPublisher = applicationComponent.anomaliesDataPublisher();
        final var deviationsDataPublisher = applicationComponent.deviationsDataPublisher();

        // Surface stray async errors during normal operation; the shutdown hook swaps this for a
        // no-op once teardown starts, since late publishes to the (now removed) broker are expected.
        RxJavaPlugins.setErrorHandler(error -> log.warn("Ignored undeliverable async error", error));

        // publish() never completes, so the pipeline only ends on failure (or on disposal at
        // shutdown, which is silent). The latch parks main — the io scheduler runs on daemon virtual
        // threads, so main returning would kill everything — and onError releases it on a real failure.
        final var done = new CountDownLatch(1);

        final var subscription = containersRunner
                .ensureDockerAvailable()
                .doOnSubscribe(_ -> progress.log("Checking Docker is installed and running …"))
                .andThen(loopbackServer
                        .obtainToken()
                        .doOnSubscribe(_ ->
                                progress.phaseSpinning("Waiting for Auth0 sign-in — complete it in your browser …")))
                .flatMap(token -> {
                    progress.phase("Provisioning HiveMQ resources (Pulse + AgentX) …");
                    return applicationComponent
                            .sessionFactory()
                            .create(token)
                            .resourceProvisioner()
                            .provision();
                })
                .flatMapCompletable(result -> {
                    progress.phase("Starting broker + orchestrator containers …");
                    final var pulseEnv = Map.of(ENV_PULSE_TOKEN, result.pulseToken());
                    final var orchestratorEnv = Map.of(
                            ENV_REGISTRATION_TOKEN, result.registrationToken(),
                            ENV_ORCHESTRATOR_ID, result.orchestratorId(),
                            ENV_ORCHESTRATOR_NAME, result.orchestratorName());

                    return containersRunner
                            .run(pulseEnv, orchestratorEnv)
                            .andThen(fromAction(
                                    () -> progress.done("Stack is up. Publishing sensor data — Ctrl+C to stop.")))
                            .andThen(mergeArray(anomaliesDataPublisher.publish(), deviationsDataPublisher.publish()));
                })
                .subscribe(done::countDown, failure -> {
                    progress.fail("Demo failed: " + failure.getMessage());
                    done.countDown();
                });

        // Registered after subscribe so the disposable is a plain final. Disposing stops publishing
        // and disconnects (via Completable.using) before teardown removes the broker; because dispose
        // is silent it never reaches onError, so no shutdown flag is needed to guard it.
        Runtime.getRuntime()
                .addShutdownHook(new Thread(
                        () -> {
                            RxJavaPlugins.setErrorHandler(_ -> {});
                            log.info("Shutting down — stopping containers …");
                            subscription.dispose();
                            containersRunner.teardown().blockingAwait();
                            log.info("Containers stopped. Goodbye.");
                        },
                        "demo-shutdown"));

        try {
            done.await();
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
