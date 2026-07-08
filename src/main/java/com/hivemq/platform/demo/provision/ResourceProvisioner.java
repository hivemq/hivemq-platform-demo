package com.hivemq.platform.demo.provision;

import static com.hivemq.platform.demo.constants.Constants.Containers.AGENT_BUS_BROKER_URL;
import static com.hivemq.platform.demo.constants.Constants.Provisioning.*;
import static io.reactivex.rxjava3.core.Single.just;
import static io.reactivex.rxjava3.core.Single.zip;

import com.hivemq.platform.demo.domain.dto.*;
import com.hivemq.platform.demo.domain.network.AgentxApi;
import com.hivemq.platform.demo.domain.network.ConsoleApi;
import com.hivemq.platform.demo.domain.network.PulseApi;
import com.hivemq.platform.demo.oauth2.SessionManager;
import io.reactivex.rxjava3.core.Single;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ResourceProvisioner {

    private final PulseApi pulse;
    private final AgentxApi agentx;
    private final ConsoleApi console;
    private final SessionManager sessionManager;

    public Single<ProvisionResult> provision() {
        return zip(pulseProvision(), agentxProvision(), (pulseResult, agentxResult) -> {
            final var pulseToken = pulseResult.pulseToken;
            final var agentxRegistrationToken = agentxResult.registrationToken;
            final var agentxOrchestratorId = agentxResult.orchestratorId;
            final var agentxOrchestratorName = agentxResult.orchestratorName;

            return new ProvisionResult(
                    pulseToken, agentxRegistrationToken, agentxOrchestratorId, agentxOrchestratorName);
        });
    }

    private record PulseResult(String pulseToken) {}

    private record AgentxResult(String orchestratorId, String orchestratorName, String registrationToken) {}

    // --- agentx

    private Single<AgentxResult> agentxProvision() {
        return ensureNetwork()
                .flatMap(network -> ensureOrchestrator(network.id()))
                .flatMap(orchestrator -> mintEnrollmentToken(orchestrator.id())
                        .flatMap(token -> ensureOrchestratorAgent(orchestrator.id())
                                .map(_ -> new AgentxResult(orchestrator.id(), orchestrator.name(), token))));
    }

    private Single<NetworkDto> ensureNetwork() {
        return agentx.listNetworks()
                .map(envelope -> envelope.data().itemsOrEmpty())
                .flatMap(networks -> {
                    if (networks.isEmpty()) {
                        final var dto = new CreateNetworkRequestDto(DEMO_NAME, DEMO_DESCRIPTION);
                        return agentx.createNetwork(dto).map(NetworkEnvelopeDto::data);
                    }

                    return just(networks.getFirst());
                })
                .doOnSuccess(network -> log.info("AgentX network ready: {}", network.name()));
    }

    private Single<OrchestratorDto> ensureOrchestrator(final String networkId) {
        return agentx.listOrchestrators(networkId)
                .map(envelope -> envelope.data().itemsOrEmpty())
                .flatMap(orchestrators -> {
                    if (orchestrators.isEmpty()) {
                        final var dto = new CreateOrchestratorRequestDto(
                                DEMO_NAME, ORCHESTRATOR_TYPE, ORCHESTRATOR_COMMUNICATION_TYPE, networkId);
                        return agentx.createOrchestrator(dto).map(OrchestratorEnvelopeDto::data);
                    }

                    return just(orchestrators.getFirst());
                })
                .doOnSuccess(orchestrator -> log.info("AgentX orchestrator ready: {}", orchestrator.name()));
    }

    private Single<String> mintEnrollmentToken(final String orchestratorId) {
        return agentx.createEnrollmentToken(orchestratorId, Map.of())
                .map(envelope -> envelope.data().plaintextToken())
                .doOnSuccess(token -> log.info("Minted agentic registration token: {}", token));
    }

    private Single<OrchestratorAgentDto> ensureOrchestratorAgent(final String orchestratorId) {

        return agentx.listOrchestratorAgents(orchestratorId)
                .map(OrchestratorAgentListEnvelopeDto::dataOrEmpty)
                .flatMap(agents -> {
                    if (agents.isEmpty()) {
                        return console.getUserConfig()
                                .map(UserConfigDto::sendGridKey)
                                .doOnSuccess(_ -> log.info("Console user-config ready."))
                                .flatMap(sendGridKey -> {
                                    final var environment = Map.of(
                                            ORCHESTRATOR_AGENT_ENV_ALERT_RECIPIENT,
                                                    sessionManager.claims().email(),
                                            ORCHESTRATOR_AGENT_ENV_FACTORY_BROKER_URL, AGENT_BUS_BROKER_URL,
                                            ORCHESTRATOR_AGENT_ENV_SENDGRID_API_KEY, sendGridKey);

                                    final var dto = new CreateOrchestratorAgentRequestDto(
                                            ORCHESTRATOR_AGENT_TEMPLATE_ID, ORCHESTRATOR_AGENT_VERSION, environment);

                                    return agentx.createOrchestratorAgent(orchestratorId, dto)
                                            .map(OrchestratorAgentEnvelopeDto::data);
                                });
                    }

                    return just(agents.getFirst());
                })
                .doOnSuccess(_ -> log.info("AgentX orchestrator agent ready."));
    }

    // --- pulse

    private Single<PulseResult> pulseProvision() {
        return ensureProject()
                .flatMap(
                        project -> ensureAgent(project.id()).flatMap(agent -> mintPulseToken(project.id(), agent.id())))
                .map(token -> new PulseResult(token));
    }

    private Single<ProjectDto> ensureProject() {
        return pulse.listProjects()
                .map(ProjectsResponseDto::itemsOrEmpty)
                .flatMap(projects -> {
                    if (projects.isEmpty()) {
                        final var dto = new CreateProjectRequestDto(
                                DEMO_NAME,
                                DEMO_DESCRIPTION,
                                sessionManager.claims().orgId());
                        return pulse.createProject(dto);
                    }

                    return just(projects.getFirst());
                })
                .doOnSuccess(project -> log.info("Pulse project ready: {}", project.name()));
    }

    private Single<AgentDto> ensureAgent(final String projectId) {
        return pulse.listAgents(projectId)
                .map(AgentsResponseDto::itemsOrEmpty)
                .flatMap(agents -> {
                    if (agents.isEmpty()) {
                        final var dto =
                                new CreateAgentRequestDto(DEMO_NAME, DEMO_DESCRIPTION, PULSE_AGENT_INFRASTRUCTURE_TYPE);
                        return pulse.createAgent(projectId, dto);
                    }

                    return just(agents.getFirst());
                })
                .doOnSuccess(agent -> log.info("Pulse agent ready: {}", agent.name()));
    }

    private Single<String> mintPulseToken(final String projectId, final String agentId) {
        return pulse.createAgentToken(projectId, agentId)
                .map(AgentTokenDto::token)
                .doOnSuccess(token -> log.info("Minted Pulse activation token: {}", token));
    }
}
