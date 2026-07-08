package com.hivemq.platform.demo.okhttp;

import com.hivemq.platform.demo.oauth2.SessionManager;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Response;
import org.jspecify.annotations.NonNull;

@Slf4j
@RequiredArgsConstructor
public class AuthorizationInterceptor implements Interceptor {

    private final SessionManager sessionManager;

    @Override
    public @NonNull Response intercept(Interceptor.Chain chain) throws IOException {
        final var token = sessionManager.token();
        return chain.proceed(chain.request()
                .newBuilder()
                .header("Authorization", token.tokenType() + " " + token.accessToken())
                .build());
    }
}
