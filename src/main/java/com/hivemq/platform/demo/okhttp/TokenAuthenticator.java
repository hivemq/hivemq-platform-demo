package com.hivemq.platform.demo.okhttp;

import com.hivemq.platform.demo.oauth2.SessionManager;
import lombok.RequiredArgsConstructor;
import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public class TokenAuthenticator implements Authenticator {

    private final SessionManager sessionManager;

    @Override
    public Request authenticate(final Route route, final @NonNull Response response) {
        if (responseCount(response) >= 2) {
            return null;
        }
        final var token = sessionManager.token();
        return response.request()
                .newBuilder()
                .header("Authorization", token.tokenType() + " " + token.accessToken())
                .build();
    }

    private static int responseCount(Response response) {
        var count = 1;
        while ((response = response.priorResponse()) != null) {
            count++;
        }
        return count;
    }
}
