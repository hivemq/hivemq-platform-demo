package com.hivemq.platform.demo.oauth2;

import com.hivemq.platform.demo.domain.dto.Oauth2TokenDto;
import java.io.IOException;

public class SessionManager {

    private final Auth0Client auth0Client;
    private volatile Oauth2TokenDto token;

    public SessionManager(final Oauth2TokenDto token, final Auth0Client auth0Client) {
        this.token = token;
        this.auth0Client = auth0Client;
    }

    public Oauth2TokenDto token() {
        return token;
    }

    public Oauth2TokenDto refreshToken() throws IOException {
        this.token = auth0Client.refreshToken(token.refreshToken());
        return token;
    }
}
