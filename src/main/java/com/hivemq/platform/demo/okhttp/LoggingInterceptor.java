package com.hivemq.platform.demo.okhttp;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Response;
import org.jspecify.annotations.NonNull;

@Slf4j
public class LoggingInterceptor implements Interceptor {

    private static final long MAX_ERROR_BODY_BYTES = 64 * 1024;

    @Override
    public @NonNull Response intercept(final Chain chain) throws IOException {
        final var request = chain.request();
        log.debug("--> {} {}", request.method(), request.url());

        final var startNanos = System.nanoTime();
        final Response response;
        try {
            response = chain.proceed(request);
        } catch (final IOException e) {
            log.warn(
                    "<-- FAILED {} {} ({}ms): {}",
                    request.method(),
                    request.url(),
                    elapsedMs(startNanos),
                    e.toString());
            throw e;
        }

        final var tookMs = elapsedMs(startNanos);
        if (response.isSuccessful()) {
            log.debug("<-- {} {} {} ({}ms)", response.code(), request.method(), request.url(), tookMs);
        } else {
            log.warn(
                    "<-- {} {} {} {} ({}ms){}",
                    response.code(),
                    response.message(),
                    request.method(),
                    request.url(),
                    tookMs,
                    errorBody(response));
        }
        return response;
    }

    private static String errorBody(final Response response) {
        try (var peeked = response.peekBody(MAX_ERROR_BODY_BYTES)) {
            final var body = peeked.string();
            return body.isBlank() ? "" : System.lineSeparator() + "    body: " + body;
        } catch (final IOException e) {
            return "";
        }
    }

    private static long elapsedMs(final long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
