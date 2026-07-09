package com.hivemq.platform.demo.di.module;

import static com.hivemq.platform.demo.constants.Constants.Mqtt.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.platform.demo.di.scope.ApplicationScope;
import com.hivemq.platform.demo.mqtt.MockDataPublisher;
import dagger.Module;
import dagger.Provides;
import io.reactivex.rxjava3.core.Scheduler;
import java.util.UUID;

@Module
public class MqttModule {

    @Provides
    @ApplicationScope
    Mqtt5BlockingClient mqttClient() {
        return Mqtt5Client.builder()
                .identifier(CLIENT_ID_PREFIX + UUID.randomUUID())
                .serverHost(BROKER_HOST)
                .serverPort(BROKER_PORT)
                .buildBlocking();
    }

    @Provides
    @ApplicationScope
    MockDataPublisher mockDataPublisher(
            Mqtt5BlockingClient mqttClient, ObjectMapper objectMapper, Scheduler ioScheduler) {
        return new MockDataPublisher(mqttClient, objectMapper, ioScheduler);
    }
}
