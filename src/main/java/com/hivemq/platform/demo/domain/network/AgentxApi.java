package com.hivemq.platform.demo.domain.network;

import com.hivemq.platform.demo.domain.dto.*;
import io.reactivex.rxjava3.core.Single;
import java.util.Map;
import retrofit2.http.*;

public interface AgentxApi {

    @GET("api/v1/networks")
    Single<NetworkListEnvelopeDto> listNetworks();

    @POST("api/v1/networks")
    Single<NetworkEnvelopeDto> createNetwork(@Body CreateNetworkRequestDto body);

    @GET("api/v1/orchestrators")
    Single<OrchestratorListEnvelopeDto> listOrchestrators(@Query("networkId") String networkId);

    @POST("api/v1/orchestrators")
    Single<OrchestratorEnvelopeDto> createOrchestrator(@Body CreateOrchestratorRequestDto body);

    @POST("api/v1/orchestrators/{orchestratorId}/enrollment-tokens")
    Single<EnrollmentTokenEnvelopeDto> createEnrollmentToken(
            @Path("orchestratorId") String orchestratorId, @Body Map<String, Object> body);

    @GET("api/v1/orchestrators/{orchestratorId}/agents")
    Single<OrchestratorAgentListEnvelopeDto> listOrchestratorAgents(@Path("orchestratorId") String orchestratorId);

    @POST("api/v1/orchestrators/{orchestratorId}/agents")
    Single<OrchestratorAgentEnvelopeDto> createOrchestratorAgent(
            @Path("orchestratorId") String orchestratorId, @Body CreateOrchestratorAgentRequestDto body);
}
