package com.portal.slideshow;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

public class AssistantActivity extends Activity {

    private static final int REQ_MEDIA = 301;

    private WebView webView;
    private TextView status;
    private PermissionRequest pendingPermissionRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(18f);
        status.setGravity(Gravity.CENTER);
        status.setBackgroundColor(Color.argb(190, 0, 0, 0));
        status.setPadding(dp(24), dp(16), dp(24), dp(16));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        slp.gravity = Gravity.CENTER;
        root.addView(status, slp);

        TextView close = new TextView(this);
        close.setText("X");
        close.setContentDescription("Close assistant");
        close.setGravity(Gravity.CENTER);
        close.setTextColor(Color.WHITE);
        close.setTextSize(22f);
        close.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        close.setClickable(true);
        close.setFocusable(true);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setShape(GradientDrawable.OVAL);
        closeBg.setColor(Color.argb(170, 7, 16, 19));
        closeBg.setStroke(dp(1), Color.argb(60, 255, 255, 255));
        close.setBackground(closeBg);
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                dp(52), dp(52));
        clp.gravity = Gravity.TOP | Gravity.RIGHT;
        clp.topMargin = dp(14);
        clp.rightMargin = dp(14);
        root.addView(close, clp);

        setContentView(root);
        configureWebView();
        requestMediaPermissionsIfNeeded();
        loadAssistant();
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                status.setVisibility(View.GONE);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (hasMediaPermissions()) {
                            request.grant(request.getResources());
                        } else {
                            pendingPermissionRequest = request;
                            requestMediaPermissionsIfNeeded();
                        }
                    }
                });
            }
        });
    }

    private void loadAssistant() {
        SharedPreferences p = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String url = p.getString(MainActivity.KEY_ASSISTANT_URL, MainActivity.DEFAULT_ASSISTANT_URL).trim();
        if (url.length() == 0) url = MainActivity.DEFAULT_ASSISTANT_URL;
        Uri assistantUri = Uri.parse(url);
        if (assistantUri.getQueryParameter("deviceId") == null) {
            url = assistantUri.buildUpon()
                    .appendQueryParameter("deviceId", MainActivity.getOrCreateDeviceId(this))
                    .build()
                    .toString();
        }
        status.setText("Loading assistant...");
        status.setVisibility(View.VISIBLE);
        webView.loadUrl(url);
    }

    private void requestMediaPermissionsIfNeeded() {
        if (!hasMediaPermissions()) {
            requestPermissions(new String[] {
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
            }, REQ_MEDIA);
        }
    }

    private boolean hasMediaPermissions() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MEDIA && pendingPermissionRequest != null) {
            if (hasMediaPermissions()) {
                pendingPermissionRequest.grant(pendingPermissionRequest.getResources());
                status.setVisibility(View.GONE);
            } else {
                pendingPermissionRequest.deny();
                status.setText("Camera and microphone permissions are needed for the assistant.");
                status.setVisibility(View.VISIBLE);
            }
            pendingPermissionRequest = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
