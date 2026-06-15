package com.hivemq.platform.demo.oauth2;

import static com.hivemq.platform.demo.constants.Constants.Loopback.REDIRECT_URI;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.platform.demo.config.Configuration;
import com.hivemq.platform.demo.domain.dto.Oauth2TokenDto;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;

@RequiredArgsConstructor
public class Auth0Client {

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final Configuration.Auth0 auth0Config;

    public Oauth2TokenDto exchangeCode(final String code, final String verifier) throws IOException {
        final var body = new FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", auth0Config.clientId())
                .add("code", code)
                .add("code_verifier", verifier)
                .add("redirect_uri", REDIRECT_URI)
                .build();
        return post(body);
    }

    public Oauth2TokenDto refreshToken(final String refreshToken) throws IOException {
        final var body = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", auth0Config.clientId())
                .add("refresh_token", refreshToken)
                .build();
        return post(body);
    }

    private Oauth2TokenDto post(final FormBody form) throws IOException {
        final var request = new Request.Builder()
                .url("https://" + auth0Config.domain() + "/oauth/token")
                .header("Accept", "application/json")
                .post(form)
                .build();
        try (var response = okHttpClient.newCall(request).execute()) {
            final var body = response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("Token request failed (HTTP " + response.code() + "): " + body);
            }
            return objectMapper.readValue(body, Oauth2TokenDto.class);
        }
    }
}
