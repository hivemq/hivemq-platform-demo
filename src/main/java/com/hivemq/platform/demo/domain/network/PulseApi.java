package com.hivemq.platform.demo.domain.network;

import com.hivemq.platform.demo.domain.dto.*;
import io.reactivex.rxjava3.core.Single;
import retrofit2.http.*;

public interface PulseApi {

    @GET("api/v1/projects")
    Single<ProjectsResponseDto> listProjects();

    @POST("api/v1/projects")
    Single<ProjectDto> createProject(@Body CreateProjectRequestDto body);

    @DELETE("api/v1/projects/{id}")
    Single<Void> deleteProject(@Path("id") String id);

    @GET("api/v1/projects/{projectId}/views/infrastructure/agents")
    Single<AgentsResponseDto> listAgents(@Path("projectId") String projectId);

    @POST("api/v1/projects/{projectId}/views/infrastructure/agents")
    Single<AgentDto> createAgent(@Path("projectId") String projectId, @Body CreateAgentRequestDto body);

    @POST("api/v1/projects/{projectId}/views/infrastructure/agents/{agentId}/tokens")
    Single<AgentTokenDto> createAgentToken(@Path("projectId") String projectId, @Path("agentId") String agentId);
}
