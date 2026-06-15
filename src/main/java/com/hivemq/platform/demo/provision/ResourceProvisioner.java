package com.hivemq.platform.demo.provision;

import static com.hivemq.platform.demo.constants.Constants.Containers.AGENT_BUS_BROKER_URL;
import static com.hivemq.platform.demo.constants.Constants.Provisioning.*;
import static io.reactivex.rxjava3.core.Single.just;
import static io.reactivex.rxjava3.core.Single.zip;

import com.hivemq.platform.demo.domain.dto.*;
import com.hivemq.platform.demo.domain.network.AgentxApi;
import com.hivemq.platform.demo.domain.network.PulseApi;
import io.reactivex.rxjava3.core.Single;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ResourceProvisioner {

    private final PulseApi pulse;
    private final AgentxApi agentx;
    private final JwtClaimsDto claims;

    public Single<ProvisionResult> provision() {
        return zip(pulseToken(), agentxToken(), ProvisionResult::from);
    }

    private Single<String> agentxToken() {
        return ensureNetwork()
                .flatMap(network -> ensureOrchestrator(network.id()))
                .flatMap(orchestrator -> {
                    final var orchestratorId = orchestrator.id();

                    return mintEnrollmentToken(orchestratorId).flatMap(token -> {
                        return ensureOrchestratorAgent(orchestratorId).map(_ -> token);
                    });
                });
    }

    private Single<String> pulseToken() {
        return ensureProject().flatMap(project -> ensureAgent(project.id())
                .flatMap(agent -> mintPulseToken(project.id(), agent.id())));
    }

    private Single<NetworkDto> ensureNetwork() {
        return agentx.listNetworks()
                .map(envelope -> envelope.data().itemsOrEmpty())
                .flatMap(networks -> {
                    final var dto = new CreateNetworkRequestDto(DEMO_NAME, DEMO_DESCRIPTION);

                    return networks.isEmpty()
                            ? agentx.createNetwork(dto).map(NetworkEnvelopeDto::data)
                            : just(networks.getFirst());
                })
                .doOnSuccess(network -> log.info("AgentX network ready: {}", network.name()));
    }

    private Single<OrchestratorDto> ensureOrchestrator(final String networkId) {
        return agentx.listOrchestrators(networkId)
                .map(envelope -> envelope.data().itemsOrEmpty())
                .flatMap(orchestrators -> {
                    final var dto = new CreateOrchestratorRequestDto(
                            DEMO_NAME, ORCHESTRATOR_TYPE, ORCHESTRATOR_COMMUNICATION_TYPE, networkId);

                    return orchestrators.isEmpty()
                            ? agentx.createOrchestrator(dto).map(OrchestratorEnvelopeDto::data)
                            : just(orchestrators.getFirst());
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
                    final var environment = Map.of(
                            ORCHESTRATOR_AGENT_ENV_ALERT_RECIPIENT,
                            claims.email(),
                            ORCHESTRATOR_AGENT_ENV_FACTORY_BROKER_URL,
                            AGENT_BUS_BROKER_URL);

                    final var dto = new CreateOrchestratorAgentRequestDto(
                            ORCHESTRATOR_AGENT_TEMPLATE_ID, ORCHESTRATOR_AGENT_VERSION, environment);

                    return agents.isEmpty()
                            ? agentx.createOrchestratorAgent(orchestratorId, dto)
                                    .map(OrchestratorAgentEnvelopeDto::data)
                            : just(agents.getFirst());
                })
                .doOnSuccess(_ -> log.info("AgentX orchestrator agent ready."));
    }

    private Single<ProjectDto> ensureProject() {
        return pulse.listProjects()
                .map(ProjectsResponseDto::itemsOrEmpty)
                .flatMap(projects -> {
                    final var dto = new CreateProjectRequestDto(DEMO_NAME, DEMO_DESCRIPTION, claims.orgId());

                    return projects.isEmpty() ? pulse.createProject(dto) : just(projects.getFirst());
                })
                .doOnSuccess(project -> log.info("Pulse project ready: {}", project.name()));
    }

    private Single<AgentDto> ensureAgent(final String projectId) {
        return pulse.listAgents(projectId)
                .map(AgentsResponseDto::itemsOrEmpty)
                .flatMap(agents -> {
                    final var dto =
                            new CreateAgentRequestDto(DEMO_NAME, DEMO_DESCRIPTION, PULSE_AGENT_INFRASTRUCTURE_TYPE);

                    return agents.isEmpty() ? pulse.createAgent(projectId, dto) : just(agents.getFirst());
                })
                .doOnSuccess(agent -> log.info("Pulse agent ready: {}", agent.name()));
    }

    private Single<String> mintPulseToken(final String projectId, final String agentId) {
        return pulse.createAgentToken(projectId, agentId)
                .map(AgentTokenDto::token)
                .doOnSuccess(token -> log.info("Minted Pulse activation token: {}", token));
    }
}
