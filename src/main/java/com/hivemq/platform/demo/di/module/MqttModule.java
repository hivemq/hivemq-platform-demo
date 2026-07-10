package com.hivemq.platform.demo.di.module;

import static com.hivemq.platform.demo.constants.Constants.Mqtt.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.platform.demo.di.scope.ApplicationScope;
import com.hivemq.platform.demo.mqtt.AnomaliesDataPublisher;
import com.hivemq.platform.demo.mqtt.DeviationsDataPublisher;
import dagger.Module;
import dagger.Provides;
import io.reactivex.rxjava3.core.Scheduler;
import java.util.UUID;

@Module
public class MqttModule {

    @Provides
    @ApplicationScope
    AnomaliesDataPublisher anomaliesDataPublisher(ObjectMapper objectMapper, Scheduler ioScheduler) {
        final var mqttClient = Mqtt5Client.builder()
                .identifier(CLIENT_ID_PREFIX + UUID.randomUUID())
                .serverHost(BROKER_HOST)
                .serverPort(BROKER_PORT)
                .buildBlocking();
        return new AnomaliesDataPublisher(mqttClient, objectMapper, ioScheduler);
    }

    @Provides
    @ApplicationScope
    DeviationsDataPublisher deviationsDataPublisher(ObjectMapper objectMapper, Scheduler ioScheduler) {
        final var mqttClient = Mqtt5Client.builder()
                .identifier(CLIENT_ID_PREFIX + UUID.randomUUID())
                .serverHost(BROKER_HOST)
                .serverPort(BROKER_PORT)
                .buildBlocking();
        return new DeviationsDataPublisher(mqttClient, objectMapper, ioScheduler);
    }
}
