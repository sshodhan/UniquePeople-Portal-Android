package com.portal.slideshow;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;

/** Shell-only entry point for deterministic physical Portal voice tests. */
public class PortalVoiceHarnessActivity extends Activity {

    private static final String PRODUCTION_ASSISTANT_URL = "https://uniquepeople-web.vercel.app/assistant";
    private static final String LOG_TAG = "PortalVoiceHarness";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String requestedUrl = decodeUrl(getIntent().getStringExtra("assistant_url_base64"));
        Uri uri = TextUtils.isEmpty(requestedUrl) ? null : Uri.parse(requestedUrl);
        if (uri != null && isAllowedAssistantUrl(uri, false)) {
            Log.i(LOG_TAG, "{\"event\":\"harness_activity_accepted\",\"timestamp\":"
                    + System.currentTimeMillis() + "}");
            Intent assistant = new Intent(this, AssistantActivity.class);
            assistant.putExtra("assistant_url", uri.toString());
            startActivity(assistant);
        } else {
            Log.w(LOG_TAG, "{\"event\":\"harness_activity_rejected\",\"timestamp\":"
                    + System.currentTimeMillis() + "}");
        }
        finish();
    }

    private static String decodeUrl(String encoded) {
        if (TextUtils.isEmpty(encoded) || encoded.length() > 8000) return "";
        try {
            return new String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    static boolean isAllowedAssistantUrl(Uri uri, boolean requireRunId) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost();
        String path = uri.getPath();
        if (host == null || !(host.equals("uniquepeople-web.vercel.app") || host.endsWith(".vercel.app"))) return false;
        if (!"/assistant".equals(path)) return false;
        String runId = uri.getQueryParameter("voiceHarnessRunId");
        String scenario = uri.getQueryParameter("voiceHarnessScenario");
        if (requireRunId) return isSafeToken(runId, 180) && isSafeToken(scenario, 120);
        if (PRODUCTION_ASSISTANT_URL.equals(uri.toString())) return true;
        return isSafeToken(runId, 180) && isSafeToken(scenario, 120);
    }

    private static boolean isSafeToken(String value, int maxLength) {
        return value != null && value.length() > 0 && value.length() <= maxLength
                && value.matches("[A-Za-z0-9._:-]+");
    }
}
