package com.hivemq.platform.demo.utils;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class OsUtils {

    public static void openUrl(final String url) {
        final String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        final List<String> cmd;
        if (os.contains("mac")) {
            cmd = List.of("open", url);
        } else if (os.contains("win")) {
            cmd = List.of("rundll32", "url.dll,FileProtocolHandler", url);
        } else {
            cmd = List.of("xdg-open", url);
        }
        try {
            new ProcessBuilder(cmd).start();
        } catch (final IOException e) {
            IO.println("(Could not open a browser automatically — open the URL above manually.)");
        }
    }
}
