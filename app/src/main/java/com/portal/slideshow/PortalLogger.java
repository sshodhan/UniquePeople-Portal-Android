package com.portal.slideshow;

import android.content.Context;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class PortalLogger {
    private static final String DEFAULT_BASE = "https://uniquepeople-web.vercel.app";

    static void event(Context context, String assistantUrl, String deviceId,
                      String event, String stage, long durationMs) {
        send(context, assistantUrl, deviceId, event, stage, durationMs, null, false);
    }

    static void error(Context context, String assistantUrl, String deviceId,
                      String event, String stage, long durationMs, Exception error) {
        send(context, assistantUrl, deviceId, event, stage, durationMs, error, true);
    }

    private static void send(final Context context, final String assistantUrl,
                             final String deviceId, final String event, final String stage,
                             final long durationMs, final Exception error, final boolean failed) {
        final Context appContext = context.getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                HttpURLConnection connection = null;
                try {
                    URL base = safeBaseUrl(assistantUrl);
                    URL endpoint = new URL(base.getProtocol(), base.getHost(), base.getPort(),
                            "/api/log-client-error");
                    JSONObject info = new JSONObject()
                            .put("_isTrace", !failed)
                            .put("source", "portal-android")
                            .put("deviceId", safe(deviceId))
                            .put("stage", safe(stage))
                            .put("durationMs", Math.max(0, durationMs))
                            .put("appVersion", appVersion(appContext));
                    if (error != null) info.put("errorClass", error.getClass().getSimpleName());
                    JSONObject payload = new JSONObject()
                            .put("message", error == null ? event : safe(error.getMessage()))
                            .put("timestamp", System.currentTimeMillis())
                            .put("errorType", event)
                            .put("activityContext", "portal-android")
                            .put("activityDisplayName", "UniquePeople Portal")
                            .put("additionalInfo", info);
                    connection = (HttpURLConnection) endpoint.openConnection();
                    connection.setConnectTimeout(5_000);
                    connection.setReadTimeout(5_000);
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setDoOutput(true);
                    try (OutputStream output = connection.getOutputStream()) {
                        output.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                    }
                    connection.getResponseCode();
                } catch (Exception ignored) {
                    // Diagnostics must never affect Portal behavior or recursively log failures.
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        }).start();
    }

    private static URL safeBaseUrl(String assistantUrl) throws Exception {
        try {
            URL parsed = new URL(assistantUrl);
            if (!"http".equals(parsed.getProtocol()) && !"https".equals(parsed.getProtocol())) {
                throw new IllegalArgumentException("Unsupported logging protocol");
            }
            return parsed;
        } catch (Exception ignored) {
            return new URL(DEFAULT_BASE);
        }
    }

    private static String appVersion(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private static String safe(String value) {
        if (value == null) return "";
        String sanitized = value.replaceAll("(?i)(memoryKey=)[^&\\s]+", "$1[redacted]");
        return sanitized.length() > 300 ? sanitized.substring(0, 300) : sanitized;
    }
}
