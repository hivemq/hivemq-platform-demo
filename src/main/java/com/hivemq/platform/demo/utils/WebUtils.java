package com.hivemq.platform.demo.utils;

public class WebUtils {

    public static String htmlPage(final String title, final String message) {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>"
                + title
                + "</title>"
                + "<style>body{font-family:system-ui,-apple-system,sans-serif;background:#0b1020;"
                + "color:#e6e9f0;display:flex;align-items:center;justify-content:center;height:100vh;"
                + "margin:0}.card{background:#161c33;padding:2rem 2.5rem;border-radius:12px;"
                + "text-align:center;box-shadow:0 10px 30px rgba(0,0,0,.4)}h1{margin:0 0 .5rem;"
                + "font-size:1.3rem}p{margin:0;color:#9aa3bd}</style></head><body><div class=\"card\">"
                + "<h1>"
                + title
                + "</h1><p>"
                + message
                + "</p></div></body></html>";
    }
}
