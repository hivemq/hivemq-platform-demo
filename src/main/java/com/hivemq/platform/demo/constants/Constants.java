package com.hivemq.platform.demo.constants;

import java.time.Duration;
import java.util.List;

public interface Constants {

    interface Api {
        Integer CONNECTION_TIMEOUT_SECONDS = 30;
    }

    interface Jwt {
        String ORGS = "orgs";
        String ID = "id";
        String PULSE = "pulse";

        String AGENTX = "agentx";
        String SERVER_URL = "serverUrl";

        // namespaced custom claim added by the Auth0 action (not the standard OIDC "email")
        String EMAIL = "https://hmqc.cloud.email";

        // optional override pinning which org to use when the user belongs to several pulse-enabled orgs
        String HIVEMQ_ORG_ID_ENV = "HIVEMQ_ORG_ID";
    }

    interface Loopback {
        Integer CALLBACK_PORT = 8585;
        String CALLBACK_HOST = "localhost";
        String CALLBACK_PATH = "/callback";

        String REDIRECT_URI = "http://" + CALLBACK_HOST + ":" + CALLBACK_PORT + CALLBACK_PATH;

        Duration ORCHESTRATOR_LOOPBACK_SERVER_TIMEOUT = Duration.ofMinutes(3);
    }

    interface Containers {

        // The orchestrator spawns agent containers (as host-daemon siblings, via the mounted socket)
        // onto this network, which it creates internally with a HARDCODED name. We put the broker +
        // orchestrator on the SAME network so agents resolve the broker by container name
        // (hivemq-broker). This couples us to an undocumented orchestrator implementation detail — if a
        // future orchestrator image renames it, agents lose the broker. See Pre-production TODOs.
        String NETWORK_NAME = "hivemq-agentic-bus";

        String BROKER_CONTAINER_NAME = "hivemq-broker";
        String BROKER_IMAGE_TAG = "hivemq-demo-broker:local";
        String BROKER_BUILD_RESOURCE_DIR = "docker/broker";
        List<String> BROKER_BUILD_FILES = List.of("Dockerfile", "pulse.xml");

        // published port bindings — match the HiveMQ image's default broker / Control Center ports
        Integer BROKER_MQTT_PORT = 1883;
        Integer BROKER_CONTROL_CENTER_PORT = 8080;

        // supplied dynamically at runtime (no defaults baked into the image)
        String ENV_PULSE_TOKEN = "PULSE_TOKEN";
        String ENV_REGISTRATION_TOKEN = "HIVEMQ_AGENTIC_REGISTRATION_TOKEN";
        String ENV_ORCHESTRATOR_ID = "ORCHESTRATOR_ID";
        String ENV_ORCHESTRATOR_NAME = "ORCHESTRATOR_NAME";

        List<String> BROKER_HEALTHCHECK_TEST = List.of("CMD-SHELL", "bash -c 'echo > /dev/tcp/localhost/1883'");
        Duration BROKER_HEALTHCHECK_INTERVAL = Duration.ofSeconds(5);
        Duration BROKER_HEALTHCHECK_TIMEOUT = Duration.ofSeconds(3);
        Integer BROKER_HEALTHCHECK_RETRIES = 30;
        Duration BROKER_HEALTHCHECK_START_PERIOD = Duration.ofSeconds(60);

        Duration HEALTH_INTERVAL = Duration.ofSeconds(3);
        Duration HEALTH_TIMEOUT = Duration.ofSeconds(60);
        String HEALTH_STATUS_HEALTHY = "healthy";

        String ORCHESTRATOR_CONTAINER_NAME = "hivemq-agentic-orch-demo";
        String ORCHESTRATOR_IMAGE = "ghcr.io/hivemq/hivemq-agentic-orch-docker:latest";
        String DOCKER_SOCK = "/var/run/docker.sock";

        String ENV_CONTROL_PLANE_URL = "CONTROL_PLANE_URL";
        String ENV_AGENT_BUS_BROKER_URL = "AGENT_BUS_BROKER_URL";
        String ENV_HIVEMQ_AGENTIC_BUS_NETWORK_NAME_SUFFIXED = "HIVEMQ_AGENTIC_BUS_NETWORK_NAME_SUFFIXED";

        String AGENT_BUS_BROKER_URL = "mqtt://hivemq-broker:1883";
        String AGENTIC_BUS_NETWORK_NAME_SUFFIXED = "false";

        // orchestrator exposes /health on port 3000 (the image ships an equivalent check; this polls
        // faster)
        List<String> ORCHESTRATOR_HEALTHCHECK_TEST = List.of(
                "CMD-SHELL",
                "node -e \"fetch('http://localhost:3000/health').then(r => r.ok ? process.exit(0) : process.exit(1))\"");
        Duration ORCHESTRATOR_HEALTHCHECK_INTERVAL = Duration.ofSeconds(5);
        Duration ORCHESTRATOR_HEALTHCHECK_TIMEOUT = Duration.ofSeconds(5);
        Integer ORCHESTRATOR_HEALTHCHECK_RETRIES = 10;
        Duration ORCHESTRATOR_HEALTHCHECK_START_PERIOD = Duration.ofSeconds(10);
    }

    interface Mqtt {
        String BROKER_HOST = "localhost";
        Integer BROKER_PORT = Containers.BROKER_MQTT_PORT;

        String TOPIC_PREFIX = "hivemq-agentic-ai-demo/factory/sensor";
        Long ANOMALIES_PUBLISH_INTERVAL_MILLIS = 1000L;
        Long DEVIATIONS_PUBLISH_INTERVAL_MILLIS = 1000L * 10L;

        // anomaly behaviour — tuned to the marketplace template's rolling-window rule.
        // ANOMALY_AFTER (warm-up) and ANOMALY_FACTOR (>20% magnitude, so each anomaly trips the rule)
        // must not be relaxed. ANOMALY_RATE is a demo knob (per-sensor, per-tick chance): each anomaly
        // trips the agent's rule and sends ONE alert email, so this directly sets email volume
        // (≈ rate × 60 × #sensors per minute → 0.01 ≈ 1-2/min). Lower it for a calmer inbox; raising it
        // is fine up to ≲0.15, beyond which frequent anomalies pollute the detector's rolling mean.
        Integer ANOMALY_AFTER = 60;
        Double ANOMALY_RATE = 0.01;
        Double ANOMALY_FACTOR = 1.30;

        String CLIENT_ID_PREFIX = "demo-sensor-publisher-";
    }

    interface Provisioning {
        String DEMO_NAME = "demo";
        String DEMO_DESCRIPTION = "demo app";

        String ORCHESTRATOR_TYPE = "docker";
        String ORCHESTRATOR_COMMUNICATION_TYPE = "http";
        String ORCHESTRATOR_AGENT_TEMPLATE_ID = "00000000-0000-4000-a000-000000000102";
        String ORCHESTRATOR_AGENT_VERSION = "1.0.9";

        // environment passed to the orchestrator agent on creation (template-specific keys)
        String ORCHESTRATOR_AGENT_ENV_ALERT_RECIPIENT = "ALERT_RECIPIENT";
        String ORCHESTRATOR_AGENT_ENV_SENDGRID_API_KEY = "SENDGRID_API_KEY";
        String ORCHESTRATOR_AGENT_ENV_FACTORY_BROKER_URL = "FACTORY_BROKER_URL";

        String PULSE_AGENT_INFRASTRUCTURE_TYPE = "ENTERPRISE";
    }
}
