package com.portal.slideshow;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Files a problem report as a Linear ticket via the uniquepeople-web
 * companion's /api/report-issue endpoint. The Linear API key lives only on
 * that server — this APK never holds it. Device context (device ID, app
 * version, Android version, model, current mode) is attached to every ticket
 * so web and Portal issues land in the same Linear project with enough detail
 * to triage. See docs/LINEAR_INTEGRATION.md.
 */
final class IssueReporter {

    interface Callback {
        void onComplete(boolean success, String message, String issueUrl);
    }

    private IssueReporter() { }

    static void reportAsync(Context context, final String title, final String description,
                            final String severity, final Callback callback) {
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            public void run() {
                boolean success = false;
                String message = "Could not send the report.";
                String issueUrl = "";
                try {
                    JSONObject payload = new JSONObject();
                    payload.put("title", title);
                    if (!TextUtils.isEmpty(description)) payload.put("description", description);
                    payload.put("severity", severity);
                    payload.put("source", "android");
                    payload.put("context", buildDeviceContext(app));

                    HttpURLConnection c = (HttpURLConnection) new URL(MainActivity.DEFAULT_REPORT_ISSUE_URL).openConnection();
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(10000);
                    c.setDoOutput(true);
                    c.setRequestMethod("POST");
                    c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    OutputStream out = c.getOutputStream();
                    out.write(payload.toString().getBytes("UTF-8"));
                    out.close();

                    int code = c.getResponseCode();
                    InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    JSONObject body = new JSONObject(emptyToJson(readText(in)));
                    if (code == 503) {
                        message = "Issue tracking is not set up on the server yet.";
                    } else if (code >= 400) {
                        String error = body.optString("error", "");
                        message = TextUtils.isEmpty(error) ? ("Report failed (HTTP " + code + ")") : error;
                    } else {
                        success = true;
                        String identifier = body.optString("identifier", "issue");
                        issueUrl = body.optString("url", "");
                        message = "Filed " + identifier + " in Linear.";
                    }
                } catch (Exception e) {
                    message = e.getMessage() == null ? message : e.getMessage();
                }

                final boolean done = success;
                final String result = message;
                final String url = issueUrl;
                main.post(new Runnable() {
                    public void run() {
                        if (callback != null) callback.onComplete(done, result, url);
                    }
                });
            }
        }).start();
    }

    private static JSONObject buildDeviceContext(Context app) throws Exception {
        SharedPreferences p = app.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        JSONObject context = new JSONObject();
        context.put("deviceId", MainActivity.getOrCreateDeviceId(app));
        String displayName = p.getString(MainActivity.KEY_DEVICE_FRIENDLY_NAME, "");
        if (!TextUtils.isEmpty(displayName)) context.put("displayName", displayName);
        try {
            PackageInfo info = app.getPackageManager().getPackageInfo(app.getPackageName(), 0);
            context.put("appVersion", info.versionName + " (" + info.versionCode + ")");
        } catch (Exception ignored) { }
        context.put("androidVersion", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        context.put("device", Build.MANUFACTURER + " " + Build.MODEL);
        context.put("mode", p.getInt(MainActivity.KEY_MODE, MainActivity.MODE_GOOGLE_PHOTOS));
        context.put("tileRenderer", p.getString(MainActivity.KEY_TILE_RENDERER, MainActivity.TILE_RENDERER_NATIVE));
        context.put("timestamp", new java.util.Date().toString());
        return context;
    }

    private static String emptyToJson(String body) {
        return TextUtils.isEmpty(body) ? "{}" : body;
    }

    private static String readText(InputStream in) throws Exception {
        if (in == null) return "";
        byte[] buffer = new byte[8192];
        StringBuilder out = new StringBuilder();
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.append(new String(buffer, 0, n, "UTF-8"));
        }
        in.close();
        return out.toString();
    }
}
