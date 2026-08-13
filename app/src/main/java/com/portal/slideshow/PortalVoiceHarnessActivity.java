package com.portal.slideshow;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

/** Shell-only entry point for deterministic physical Portal voice tests. */
public class PortalVoiceHarnessActivity extends Activity {

    private static final String PRODUCTION_ASSISTANT_URL = "https://uniquepeople-web.vercel.app/assistant";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String requestedUrl = getIntent().getStringExtra("assistant_url");
        Uri uri = TextUtils.isEmpty(requestedUrl) ? null : Uri.parse(requestedUrl);
        if (uri != null && isAllowedAssistantUrl(uri, false)) {
            Intent assistant = new Intent(this, AssistantActivity.class);
            assistant.putExtra("assistant_url", uri.toString());
            startActivity(assistant);
        }
        finish();
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
