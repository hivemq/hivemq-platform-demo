package com.hivemq.platform.demo.okhttp;

import com.hivemq.platform.demo.oauth2.SessionManager;
import java.io.IOException;
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
    public Request authenticate(final Route route, final @NonNull Response response) throws IOException {
        if (responseCount(response) >= 2) {
            return null; // already retried once with a refreshed token; give up to avoid a loop
        }
        synchronized (sessionManager) {
            final var current = sessionManager.token();
            final var currentHeader = current.tokenType() + " " + current.accessToken();
            // if another 401 already refreshed while we waited on the lock, reuse that token
            final var token = currentHeader.equals(response.request().header("Authorization"))
                    ? sessionManager.refreshToken()
                    : current;
            return response.request()
                    .newBuilder()
                    .header("Authorization", token.tokenType() + " " + token.accessToken())
                    .build();
        }
    }

    private static int responseCount(Response response) {
        var count = 1;
        while ((response = response.priorResponse()) != null) {
            count++;
        }
        return count;
    }
}
