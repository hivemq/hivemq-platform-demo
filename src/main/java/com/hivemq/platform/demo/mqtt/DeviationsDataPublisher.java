package com.hivemq.platform.demo.mqtt;

import static com.hivemq.platform.demo.constants.Constants.Mqtt.*;
import static io.reactivex.rxjava3.core.Completable.fromAction;
import static io.reactivex.rxjava3.core.Completable.using;
import static io.reactivex.rxjava3.core.Flowable.interval;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.platform.demo.domain.dto.DeviationsSensorReadingDto;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.functions.Supplier;
import java.util.ArrayList;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class DeviationsDataPublisher {

    private final Mqtt5BlockingClient mqttClient;
    private final ObjectMapper objectMapper;
    private final Scheduler ioScheduler;

    public Completable publish() {
        return using(connectClient(), this::publish, disconnectClient()).subscribeOn(ioScheduler);
    }

    private Supplier<Mqtt5BlockingClient> connectClient() {
        return () -> {
            mqttClient.connect();
            log.info("Connected to broker at {}:{} — publishing mock sensor data.", BROKER_HOST, BROKER_PORT);
            return mqttClient;
        };
    }

    private Consumer<Mqtt5BlockingClient> disconnectClient() {
        return Mqtt5BlockingClient::disconnect;
    }

    private Completable publish(Mqtt5BlockingClient client) {
        return interval(0, DEVIATIONS_PUBLISH_INTERVAL_MILLIS, MILLISECONDS, ioScheduler)
                .concatMapIterable(_ -> {
                    final var mqtt5Publishes = new ArrayList<Mqtt5Publish>();

                    for (final var sensor : SensorProfile.DEFAULTS) {
                        final var topic = TOPIC_PREFIX + "/" + sensor.name();
                        final var payload = new DeviationsSensorReadingDto(
                                UUID.randomUUID().toString(), UUID.randomUUID().toString(), System.currentTimeMillis());

                        log.info("{} payload={}", topic, payload);

                        final var publish = Mqtt5Publish.builder()
                                .topic(topic)
                                .qos(MqttQos.AT_MOST_ONCE)
                                .payload(objectMapper.writeValueAsBytes(payload))
                                .build();
                        mqtt5Publishes.add(publish);
                    }

                    return mqtt5Publishes;
                })
                .concatMapCompletable(message -> fromAction(() -> client.publish(message)));
    }
}
