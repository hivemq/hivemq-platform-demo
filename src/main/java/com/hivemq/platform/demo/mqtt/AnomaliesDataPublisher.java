package com.hivemq.platform.demo.mqtt;

import static com.hivemq.platform.demo.constants.Constants.Mqtt.*;
import static io.reactivex.rxjava3.core.Completable.fromAction;
import static io.reactivex.rxjava3.core.Completable.using;
import static io.reactivex.rxjava3.core.Flowable.interval;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.platform.demo.domain.dto.AnomaliesSensorReadingDto;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.functions.Supplier;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class AnomaliesDataPublisher {

    private final Mqtt5BlockingClient mqttClient;
    private final ObjectMapper objectMapper;
    private final Scheduler ioScheduler;

    private final Random random = new Random();

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

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
        return interval(0, ANOMALIES_PUBLISH_INTERVAL_MILLIS, MILLISECONDS, ioScheduler)
                .map(tick -> tick + 1)
                .concatMapIterable(this::publish)
                .concatMapCompletable(message -> fromAction(() -> client.publish(message)));
    }

    private List<Mqtt5Publish> publish(long sampleN) throws JsonProcessingException {
        final var armed = sampleN >= ANOMALY_AFTER;
        final var ts = System.currentTimeMillis();
        final var publishes = new ArrayList<Mqtt5Publish>(SensorProfile.DEFAULTS.size());

        for (final var sensor : SensorProfile.DEFAULTS) {
            final var anomaly = armed && random.nextDouble() < ANOMALY_RATE;
            final var value = round3(anomaly ? anomalyValue(sensor) : baselineValue(sensor));
            final var topic = TOPIC_PREFIX + "/" + sensor.name();

            log.info("{} value={} {}{}", topic, value, sensor.unit(), anomaly ? "  ← anomaly" : "");

            final var mqtt5Publish = Mqtt5Publish.builder()
                    .topic(topic)
                    .qos(MqttQos.AT_MOST_ONCE)
                    .payload(objectMapper.writeValueAsBytes(new AnomaliesSensorReadingDto(value, sensor.unit(), ts)))
                    .build();

            publishes.add(mqtt5Publish);
        }

        return publishes;
    }

    private double baselineValue(SensorProfile sensor) {
        return random.nextGaussian() * sensor.stddev() + sensor.mean();
    }

    private double anomalyValue(SensorProfile sensor) {
        final var direction = random.nextBoolean() ? 1 : -1;
        return sensor.mean() * (1 + direction * (ANOMALY_FACTOR - 1)) + random.nextGaussian() * (sensor.stddev() / 2);
    }
}
