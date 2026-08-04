package com.portal.slideshow;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class QrScanActivity extends Activity {

    private static final int REQ_CAMERA = 41;

    private WebView scannerView;
    private PermissionRequest pendingPermissionRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        scannerView = new WebView(this);
        configureScannerView();
        root.addView(scannerView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        Button close = new Button(this);
        close.setText("Close");
        close.setAllCaps(false);
        close.setTextSize(18f);
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        closeLp.gravity = Gravity.TOP | Gravity.LEFT;
        closeLp.leftMargin = dp(16);
        closeLp.topMargin = dp(16);
        root.addView(close, closeLp);

        setContentView(root);
        loadScannerPage();
    }

    private void configureScannerView() {
        WebSettings settings = scannerView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);

        scannerView.setBackgroundColor(Color.BLACK);
        scannerView.setWebViewClient(new WebViewClient());
        scannerView.addJavascriptInterface(new QrBridge(), "UniquePeopleQr");
        scannerView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (hasCameraPermission()) {
                            request.grant(request.getResources());
                        } else {
                            pendingPermissionRequest = request;
                            requestPermissions(new String[] { Manifest.permission.CAMERA }, REQ_CAMERA);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && pendingPermissionRequest != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pendingPermissionRequest.grant(pendingPermissionRequest.getResources());
            } else {
                pendingPermissionRequest.deny();
                Toast.makeText(this, "Camera permission is needed to scan a QR code.", Toast.LENGTH_LONG).show();
            }
            pendingPermissionRequest = null;
        }
    }

    @Override
    protected void onDestroy() {
        if (scannerView != null) {
            scannerView.loadUrl("about:blank");
            scannerView.destroy();
            scannerView = null;
        }
        super.onDestroy();
    }

    private boolean hasCameraPermission() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void loadScannerPage() {
        try {
            String html = readAsset("qr-scan.html");
            String js = readAsset("qr-scan.js");
            html = html.replace("<script src=\"qr-scan.js\"></script>", "<script>\n" + js + "\n</script>");
            scannerView.loadDataWithBaseURL("https://uniquepeople.local/", html, "text/html", "UTF-8", null);
        } catch (Exception e) {
            Toast.makeText(this, "Could not load the QR scanner.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private String readAsset(String name) throws Exception {
        InputStream in = getAssets().open(name);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return out.toString("UTF-8");
        } finally {
            in.close();
        }
    }

    private void handleQrCode(String rawValue) {
        String albumUrl = rawValue == null ? "" : rawValue.trim();
        if (TextUtils.isEmpty(albumUrl) || !isValidWebUrl(albumUrl)) {
            Toast.makeText(this, "This QR code does not contain a valid web link.", Toast.LENGTH_LONG).show();
            return;
        }

        SharedPreferences.Editor editor = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit();
        editor.putString(MainActivity.KEY_ALBUM_URL, albumUrl);
        editor.putInt(MainActivity.KEY_MODE, MainActivity.MODE_GOOGLE_PHOTOS);
        editor.apply();

        Intent result = new Intent();
        result.putExtra("album_url", albumUrl);
        setResult(RESULT_OK, result);
        Toast.makeText(this, "Album QR code saved.", Toast.LENGTH_SHORT).show();
        finish();
    }

    private boolean isValidWebUrl(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private class QrBridge {
        @JavascriptInterface
        public void onQrCode(final String rawValue) {
            runOnUiThread(new Runnable() {
                public void run() { handleQrCode(rawValue); }
            });
        }

        @JavascriptInterface
        public void onScannerError(final String message) {
            runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(QrScanActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}
