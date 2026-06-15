package com.hivemq.platform.demo.di.module;

import static com.hivemq.platform.demo.constants.Constants.Api.CONNECTION_TIMEOUT_SECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.platform.demo.config.Configuration;
import com.hivemq.platform.demo.console.ConsoleProgress;
import com.hivemq.platform.demo.di.scope.ApplicationScope;
import com.hivemq.platform.demo.oauth2.Auth0Client;
import com.hivemq.platform.demo.oauth2.LoopbackServer;
import com.hivemq.platform.demo.okhttp.LoggingInterceptor;
import dagger.Module;
import dagger.Provides;
import io.reactivex.rxjava3.core.Scheduler;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Module
public class NetworkModule {

    @Provides
    @ApplicationScope
    LoopbackServer loopbackServer(
            Scheduler ioScheduler,
            Auth0Client auth0Client,
            Configuration.Auth0 auth0Config,
            ConsoleProgress consoleProgress) {
        return new LoopbackServer(ioScheduler, auth0Client, auth0Config, consoleProgress);
    }

    @Provides
    @ApplicationScope
    Auth0Client auth0Client(OkHttpClient okHttpClient, ObjectMapper objectMapper, Configuration.Auth0 auth0Config) {
        return new Auth0Client(okHttpClient, objectMapper, auth0Config);
    }

    @Provides
    @ApplicationScope
    LoggingInterceptor loggingInterceptor() {
        return new LoggingInterceptor();
    }

    @Provides
    @ApplicationScope
    OkHttpClient okHttpClient(LoggingInterceptor loggingInterceptor) {
        return new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .readTimeout(CONNECTION_TIMEOUT_SECONDS, SECONDS)
                .writeTimeout(CONNECTION_TIMEOUT_SECONDS, SECONDS)
                .connectTimeout(CONNECTION_TIMEOUT_SECONDS, SECONDS)
                .build();
    }

    @Provides
    @ApplicationScope
    Retrofit retrofit(OkHttpClient okHttpClient, Scheduler ioScheduler, ObjectMapper objectMapper) {
        return new Retrofit.Builder()
                .baseUrl("http://localhost:8080/")
                .client(okHttpClient)
                .addConverterFactory(JacksonConverterFactory.create(objectMapper))
                .addCallAdapterFactory(RxJava3CallAdapterFactory.createWithScheduler(ioScheduler))
                .build();
    }
}
