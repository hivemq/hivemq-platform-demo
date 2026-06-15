package com.hivemq.platform.demo.oauth2;

import static com.hivemq.platform.demo.constants.Constants.Loopback.*;
import static com.hivemq.platform.demo.utils.OsUtils.openUrl;
import static com.hivemq.platform.demo.utils.UrlUtils.encode;
import static com.hivemq.platform.demo.utils.UrlUtils.parseQuery;
import static com.hivemq.platform.demo.utils.WebUtils.htmlPage;
import static io.reactivex.rxjava3.core.Single.fromCallable;
import static io.reactivex.rxjava3.core.Single.using;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.hivemq.platform.demo.config.Configuration;
import com.hivemq.platform.demo.console.ConsoleProgress;
import com.hivemq.platform.demo.domain.dto.Oauth2TokenDto;
import com.hivemq.platform.demo.utils.PkceUtils;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.functions.Supplier;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public class LoopbackServer {

    private final Scheduler ioScheduler;
    private final Auth0Client auth0Client;
    private final Configuration.Auth0 auth0Config;
    private final ConsoleProgress progress;

    public Single<Oauth2TokenDto> obtainToken() {
        return using(createServer(), this::obtainToken, terminateServer())
                .timeout(3, MINUTES)
                .subscribeOn(ioScheduler);
    }

    private Supplier<ServerSocket> createServer() {
        return () -> new ServerSocket(CALLBACK_PORT, 0, InetAddress.getByName(CALLBACK_HOST));
    }

    private Consumer<ServerSocket> terminateServer() {
        return ServerSocket::close;
    }

    private Single<Oauth2TokenDto> obtainToken(final ServerSocket server) {
        return fromCallable(() -> {
            final var verifier = PkceUtils.verifier();
            final var challenge = PkceUtils.challenge(verifier);
            final var state = PkceUtils.state();

            final var authorizeUrl = authorizeUrl(challenge, state);
            progress.log("Open this URL in your browser to authorize:\n" + authorizeUrl);
            openUrl(authorizeUrl);

            try (var socket = server.accept()) {
                final var requestSegments = extractRequestSegments(socket);

                final var tokenRequestPath = requestSegments[1];

                final var params = parseQuery(tokenRequestPath);
                final var error = params.get("error");
                final var code = params.get("code");
                final var ok = error == null && code != null && state.equals(params.get("state"));
                final var html = ok
                        ? htmlPage("Authorized", "Please return to the terminal.")
                        : htmlPage("Authorization failed", "Please return to the terminal.");

                writeResponse(socket, html);

                if (!ok) {
                    throw new IllegalStateException(
                            error != null
                                    ? "Authorization error: " + error
                                    : "Missing authorization code or state mismatch");
                }
                return auth0Client.exchangeCode(code, verifier);
            }
        });
    }

    private String @NonNull [] extractRequestSegments(Socket socket) throws IOException {
        final var inputStreamReader = new InputStreamReader(socket.getInputStream(), UTF_8);
        final var bufferedReader = new BufferedReader(inputStreamReader);
        final var requestLine = bufferedReader.readLine();
        final var malformedCallbackRequest = new IllegalStateException("Malformed callback request");
        if (requestLine == null) throw malformedCallbackRequest;
        final var requestSegments = requestLine.split(" ");
        if (requestSegments.length < 2) throw malformedCallbackRequest;
        return requestSegments;
    }

    private void writeResponse(final Socket socket, final String html) throws IOException {
        final var body = html.getBytes(UTF_8);
        final var out = socket.getOutputStream();
        out.write(("HTTP/1.1 200 OK\r\n"
                        + "Content-Type: text/html; charset=utf-8\r\n"
                        + "Content-Length: "
                        + body.length
                        + "\r\n"
                        + "Connection: close\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private String authorizeUrl(final String challenge, final String state) {

        return "https://"
                + auth0Config.domain()
                + "/authorize?response_type=code"
                + "&client_id="
                + encode(auth0Config.clientId())
                + "&redirect_uri="
                + encode(REDIRECT_URI)
                + "&scope="
                + encode(auth0Config.scope())
                + "&audience="
                + encode(auth0Config.audience())
                + "&code_challenge="
                + challenge
                + "&code_challenge_method=S256"
                + "&state="
                + state;
    }
}
