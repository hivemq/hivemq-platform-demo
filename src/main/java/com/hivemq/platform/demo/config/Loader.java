package com.hivemq.platform.demo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Loader {

    private static final String RESOURCE = "/application.yaml";

    private final ObjectMapper mapper;

    public Configuration load() {
        try (InputStream in = Loader.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing bundled config resource: " + RESOURCE);
            }
            return mapper.readValue(in, Configuration.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read bundled config resource: " + RESOURCE, e);
        }
    }
}
