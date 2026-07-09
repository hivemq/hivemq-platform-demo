package com.hivemq.platform.demo.di.module;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.hivemq.platform.demo.config.Configuration;
import com.hivemq.platform.demo.containers.ContainersRunner;
import com.hivemq.platform.demo.containers.DockerManager;
import com.hivemq.platform.demo.di.scope.ApplicationScope;
import dagger.Module;
import dagger.Provides;
import io.reactivex.rxjava3.core.Scheduler;

@Module
public class DockerModule {

    @Provides
    @ApplicationScope
    DockerClient dockerClient() {
        final var config =
                DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        final var httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .build();
        return DockerClientImpl.getInstance(config, httpClient);
    }

    @Provides
    @ApplicationScope
    DockerManager dockerManager(DockerClient dockerClient, Scheduler ioScheduler) {
        return new DockerManager(dockerClient, ioScheduler);
    }

    @Provides
    @ApplicationScope
    ContainersRunner containersRunner(DockerManager dockerManager, Scheduler ioScheduler, Configuration configuration) {
        return new ContainersRunner(ioScheduler, dockerManager, configuration);
    }
}
