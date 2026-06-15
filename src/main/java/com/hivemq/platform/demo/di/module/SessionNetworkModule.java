package com.hivemq.platform.demo.di.module;

import static com.hivemq.platform.demo.utils.JwtUtils.decodeClaims;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.platform.demo.config.Configuration;
import com.hivemq.platform.demo.di.qualifier.Agentx;
import com.hivemq.platform.demo.di.qualifier.Authenticated;
import com.hivemq.platform.demo.di.qualifier.Pulse;
import com.hivemq.platform.demo.di.scope.SessionScope;
import com.hivemq.platform.demo.domain.dto.JwtClaimsDto;
import com.hivemq.platform.demo.domain.dto.Oauth2TokenDto;
import com.hivemq.platform.demo.domain.network.AgentxApi;
import com.hivemq.platform.demo.domain.network.PulseApi;
import com.hivemq.platform.demo.oauth2.Auth0Client;
import com.hivemq.platform.demo.oauth2.SessionManager;
import com.hivemq.platform.demo.okhttp.AuthorizationInterceptor;
import com.hivemq.platform.demo.okhttp.TokenAuthenticator;
import com.hivemq.platform.demo.provision.ResourceProvisioner;
import dagger.Module;
import dagger.Provides;
import okhttp3.Authenticator;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

@Module
public class SessionNetworkModule {

    @Provides
    @SessionScope
    SessionManager sessionManager(Oauth2TokenDto token, Auth0Client auth0Client) {
        return new SessionManager(token, auth0Client);
    }

    @Provides
    @SessionScope
    Interceptor authorizationInterceptor(SessionManager sessionManager) {
        return new AuthorizationInterceptor(sessionManager);
    }

    @Provides
    @SessionScope
    Authenticator tokenAuthenticator(SessionManager sessionManager) {
        return new TokenAuthenticator(sessionManager);
    }

    @Provides
    @SessionScope
    JwtClaimsDto jwtClaimsDto(
            SessionManager sessionManager, ObjectMapper objectMapper, Configuration.Fallback fallback) {
        final var claims = decodeClaims(objectMapper, sessionManager.token().accessToken());
        return JwtClaimsDto.from(claims, fallback);
    }

    @Provides
    @Pulse
    String pulseBaseUrl(JwtClaimsDto claims) {
        return claims.pulseBaseUrl();
    }

    @Provides
    @Agentx
    String agentxBaseUrl(JwtClaimsDto claims) {
        return claims.agentxBaseUrl();
    }

    @Provides
    @SessionScope
    @Authenticated
    OkHttpClient authenticatedClient(
            OkHttpClient okHttpClient, Interceptor authorizationInterceptor, Authenticator tokenAuthenticator) {
        return okHttpClient
                .newBuilder()
                .addInterceptor(authorizationInterceptor)
                .authenticator(tokenAuthenticator)
                .build();
    }

    @Provides
    @SessionScope
    @Pulse
    Retrofit pulseRetrofit(@Pulse String baseUrl, @Authenticated OkHttpClient okHttpClient, Retrofit retrofit) {
        return retrofit.newBuilder().baseUrl(baseUrl).client(okHttpClient).build();
    }

    @Provides
    @SessionScope
    @Agentx
    Retrofit agentxRetrofit(@Agentx String baseUrl, @Authenticated OkHttpClient okHttpClient, Retrofit retrofit) {
        return retrofit.newBuilder().baseUrl(baseUrl).client(okHttpClient).build();
    }

    @Provides
    @SessionScope
    PulseApi pulseApi(@Pulse Retrofit retrofit) {
        return retrofit.create(PulseApi.class);
    }

    @Provides
    @SessionScope
    AgentxApi agentxApi(@Agentx Retrofit retrofit) {
        return retrofit.create(AgentxApi.class);
    }

    @Provides
    @SessionScope
    ResourceProvisioner resourceProvisioner(PulseApi pulseApi, AgentxApi agentxApi, JwtClaimsDto claims) {
        return new ResourceProvisioner(pulseApi, agentxApi, claims);
    }
}
