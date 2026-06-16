package com.hivemq.platform.demo.di.module;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.platform.demo.config.Configuration;
import com.hivemq.platform.demo.di.qualifier.Agentx;
import com.hivemq.platform.demo.di.qualifier.Authenticated;
import com.hivemq.platform.demo.di.qualifier.Console;
import com.hivemq.platform.demo.di.qualifier.Pulse;
import com.hivemq.platform.demo.di.scope.SessionScope;
import com.hivemq.platform.demo.domain.dto.Oauth2TokenDto;
import com.hivemq.platform.demo.domain.network.AgentxApi;
import com.hivemq.platform.demo.domain.network.ConsoleApi;
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
    SessionManager sessionManager(
            Oauth2TokenDto token, Auth0Client auth0Client, ObjectMapper objectMapper, Configuration.Fallback fallback) {
        return new SessionManager(token, auth0Client, objectMapper, fallback);
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

    @Pulse
    @Provides
    @SessionScope
    String pulseBaseUrl(SessionManager sessionManager) {
        return sessionManager.claims().pulseBaseUrl();
    }

    @Agentx
    @Provides
    @SessionScope
    String agentxBaseUrl(SessionManager sessionManager) {
        return sessionManager.claims().agentxBaseUrl();
    }

    @Console
    @Provides
    @SessionScope
    String consoleBaseUrl(Configuration.Fallback fallback) {
        return fallback.consoleBaseUrl();
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
    @Console
    Retrofit consoleRetrofit(@Console String baseUrl, @Authenticated OkHttpClient okHttpClient, Retrofit retrofit) {
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
    ConsoleApi consoleApi(@Console Retrofit retrofit) {
        return retrofit.create(ConsoleApi.class);
    }

    @Provides
    @SessionScope
    ResourceProvisioner resourceProvisioner(
            PulseApi pulseApi, AgentxApi agentxApi, ConsoleApi consoleApi, SessionManager sessionManager) {
        return new ResourceProvisioner(pulseApi, agentxApi, consoleApi, sessionManager);
    }
}
