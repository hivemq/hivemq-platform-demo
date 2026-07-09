package com.hivemq.platform.demo.utils;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class UrlUtils {

    public static Map<String, String> parseQuery(final String uri) {
        return parseQuery(URI.create(uri));
    }

    public static Map<String, String> parseQuery(final URI uri) {
        final Map<String, String> map = new HashMap<>();
        final String raw = uri.getRawQuery();
        if (raw == null) {
            return map;
        }
        for (final String pair : raw.split("&")) {
            final int eq = pair.indexOf('=');
            if (eq > 0) {
                map.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
            }
        }
        return map;
    }

    public static String decode(final String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    public static String encode(final String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
