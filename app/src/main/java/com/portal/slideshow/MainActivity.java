package com.portal.slideshow;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;
import android.webkit.WebSettings;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

import org.json.JSONObject;

public class MainActivity extends Activity {

    static final String PREFS = "slideshow_prefs";
    static final String KEY_URL = "video_url";
    static final String KEY_ALBUM_URL = "album_url";
    static final String KEY_PHOTO_HOST_URL = "photo_host_url";
    static final String KEY_MODE = "mode";
    static final String KEY_ASSISTANT_URL = "assistant_url";
    static final String KEY_DEVICE_ID = "device_id";
    static final String DEFAULT_SETTINGS_BASE_URL = "https://uniquepeople-web.vercel.app/settings";
    static final String DEFAULT_REMOTE_CONFIG_URL = "https://uniquepeople-web.vercel.app/api/device-config";
    static final String DEFAULT_ALBUM_URL = "https://photos.app.goo.gl/qsgZFqbeTfpmWUvdA";
    static final String PREVIOUS_DEFAULT_ALBUM_URL = "https://photos.app.goo.gl/HLFtGT4sZbh6DnjP9";
    static final String DEFAULT_ASSISTANT_URL = "https://uniquepeople-web.vercel.app/assistant";
    static final String DEFAULT_PHOTO_HOST_URL = "https://uniquepeople-web.vercel.app/photo-host";
    static final int MODE_BUNDLED = 0;
    static final int MODE_STREAM = 1;
    static final int MODE_DOWNLOAD = 2;
    static final int MODE_GOOGLE_PHOTOS = 3;
    static final int MODE_PHOTO_HOST = 4;
    private static final String ASSET_NAME = "slideshow.mp4";
    private static final String DEFAULT_PHOTO_DIR = "default_photos";
    private static final long DEFAULT_PHOTO_DELAY_MS = 8000;
    private static final int ALBUM_INITIAL_SCALE_PERCENT = 125;
    private static final float ALBUM_PAGE_ZOOM = 1.15f;
    private static final int REQ_SETTINGS = 100;

    private VideoView video;
    private WebView albumView;
    private ImageView defaultPhoto;
    private TextView status;
    private View overlay;
    private Button gear;
    private Button assistant;
    private String[] defaultPhotoNames;
    private int defaultPhotoIndex;
    private boolean albumZoomApplied;
    private boolean albumLoadFailed;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable hideGear = new Runnable() {
        public void run() {
            if (gear != null) gear.setVisibility(View.GONE);
            if (assistant != null) assistant.setVisibility(View.GONE);
            if (overlay != null) {
                overlay.setClickable(false);
                overlay.setVisibility(View.GONE);
            }
        }
    };
    private final Runnable advanceDefaultPhoto = new Runnable() {
        public void run() {
            if (defaultPhoto == null || defaultPhoto.getVisibility() != View.VISIBLE) return;
            showNextDefaultPhoto();
            ui.postDelayed(this, DEFAULT_PHOTO_DELAY_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        video = new VideoView(this);
        FrameLayout.LayoutParams vlp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        vlp.gravity = Gravity.CENTER;
        root.addView(video, vlp);

        defaultPhoto = new ImageView(this);
        defaultPhoto.setBackgroundColor(Color.BLACK);
        defaultPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        defaultPhoto.setClickable(true);
        defaultPhoto.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    revealGear();
                }
                return false;
            }
        });
        defaultPhoto.setVisibility(View.GONE);
        root.addView(defaultPhoto, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        albumView = new WebView(this);
        configureAlbumView();
        albumView.setVisibility(View.GONE);
        root.addView(albumView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(20f);
        status.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        slp.gravity = Gravity.CENTER;
        status.setVisibility(View.GONE);
        root.addView(status, slp);

        // Kept below controls for older modes; Google Photos must receive all album touches.
        overlay = new View(this);
        overlay.setClickable(false);
        overlay.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    revealGear();
                }
                return false;
            }
        });
        overlay.setVisibility(View.GONE);
        root.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        gear = new Button(this);
        gear.setText("⚙  Settings");
        gear.setTextSize(18f);
        gear.setVisibility(View.GONE);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        glp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        glp.leftMargin = 40;
        glp.bottomMargin = 40;
        gear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openSettings(); }
        });
        root.addView(gear, glp);

        assistant = new Button(this);
        assistant.setText("Assistant");
        assistant.setTextSize(18f);
        assistant.setVisibility(View.GONE);
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        alp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        alp.rightMargin = 40;
        alp.bottomMargin = 40;
        assistant.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openAssistant(); }
        });
        root.addView(assistant, alp);

        setContentView(root);

        video.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            public void onPrepared(MediaPlayer mp) {
                mp.setLooping(true);
                hideStatus();
                video.start();
            }
        });
        video.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            public boolean onError(MediaPlayer mp, int what, int extra) {
                showStatus("Couldn't play this video.\nTap the screen, then open Settings.");
                return true;
            }
        });

        getOrCreateDeviceId(this);
        showStatus("Checking device settings...");
        refreshRemoteConfigAsync(this, new RemoteConfigCallback() {
            public void onComplete(boolean success, String message) {
                loadAndPlay();
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP && controlsAreHidden()) {
            revealGear();
        }
        return super.dispatchTouchEvent(event);
    }

    private void loadAndPlay() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String url = p.getString(KEY_URL, "");
        String albumUrl = getAlbumUrl(p);
        String photoHostUrl = p.getString(KEY_PHOTO_HOST_URL, DEFAULT_PHOTO_HOST_URL);
        int defaultMode = MODE_GOOGLE_PHOTOS;
        int mode = p.getInt(KEY_MODE, defaultMode);

        if (mode == MODE_GOOGLE_PHOTOS || mode == MODE_PHOTO_HOST) {
            if (!TextUtils.isEmpty(albumUrl)) {
                if (mode == MODE_PHOTO_HOST) {
                    showAlbum(buildPhotoHostUrl(photoHostUrl, albumUrl), "Loading Photo Host...");
                } else {
                    showAlbum(albumUrl, "Loading shared photo link...");
                }
            } else {
                showDefaultPhotos();
            }
            return;
        }

        hideAlbum();
        hideDefaultPhotos();

        if (mode == MODE_BUNDLED || TextUtils.isEmpty(url)) {
            File f = ensureLocalCopy();
            if (f != null) {
                playFile(f);
            } else {
                showDefaultPhotos();
            }
            return;
        }
        if (mode == MODE_STREAM) {
            showStatus("Loading…");
            video.setVideoURI(Uri.parse(url));
        } else {
            startDownload(url);
        }
    }

    private void playFile(File f) {
        hideAlbum();
        hideDefaultPhotos();
        video.setVideoURI(Uri.fromFile(f));
    }

    private void configureAlbumView() {
        WebSettings settings = albumView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        albumView.setInitialScale(ALBUM_INITIAL_SCALE_PERCENT);
        albumView.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP && controlsAreHidden()) {
                    revealGear();
                    return true;
                }
                return false;
            }
        });
        albumView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request == null || request.isForMainFrame()) {
                    showAlbumSharingHelp();
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                if (request == null || request.isForMainFrame()) {
                    int code = errorResponse == null ? 0 : errorResponse.getStatusCode();
                    if (code >= 400) showAlbumSharingHelp();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (isSharingProblemUrl(url)) {
                    showAlbumSharingHelp();
                    return;
                }
                if (albumLoadFailed) return;
                hideStatus();
                applyAlbumZoom(view);
            }
        });
    }

    private void applyAlbumZoom(final WebView view) {
        if (albumZoomApplied) return;
        albumZoomApplied = true;
        view.postDelayed(new Runnable() {
            public void run() {
                if (albumView != null && albumView.getVisibility() == View.VISIBLE) {
                    view.zoomBy(ALBUM_PAGE_ZOOM);
                }
            }
        }, 500);
    }

    private String buildPhotoHostUrl(String photoHostUrl, String albumUrl) {
        String base = TextUtils.isEmpty(photoHostUrl) ? DEFAULT_PHOTO_HOST_URL : photoHostUrl;
        String separator = base.contains("?") ? "&" : "?";
        try {
            return base + separator + "albumUrl=" + URLEncoder.encode(albumUrl, "UTF-8");
        } catch (Exception e) {
            return base + separator + "albumUrl=" + albumUrl;
        }
    }

    private void showAlbum(String albumUrl, String loadingText) {
        hideDefaultPhotos();
        video.stopPlayback();
        video.setVisibility(View.GONE);
        albumView.setVisibility(View.VISIBLE);
        showStatus(loadingText);
        albumZoomApplied = false;
        albumLoadFailed = false;
        albumView.setInitialScale(ALBUM_INITIAL_SCALE_PERCENT);
        albumView.loadUrl(albumUrl);
        revealGear();
    }

    private void showAlbumSharingHelp() {
        albumLoadFailed = true;
        if (albumView != null) {
            albumView.stopLoading();
            albumView.setVisibility(View.GONE);
        }
        if (video != null) {
            video.stopPlayback();
            video.setVisibility(View.GONE);
        }
        hideDefaultPhotos();
        showStatus("Album sharing needs an update\n\nOpen Google Photos, turn on shared-link access for this album, then tap Settings to paste or scan the updated link.");
        revealGear();
    }

    private boolean isSharingProblemUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.contains("accounts.google.com")
                || lower.contains("/signin")
                || lower.contains("servicelogin");
    }

    private void hideAlbum() {
        if (albumView != null) {
            albumView.setVisibility(View.GONE);
        }
        if (video != null) {
            video.setVisibility(View.VISIBLE);
        }
        if (overlay != null) {
            overlay.setClickable(false);
            overlay.setVisibility(View.GONE);
        }
    }

    private void showDefaultPhotos() {
        hideAlbum();
        video.stopPlayback();
        video.setVisibility(View.GONE);
        defaultPhoto.setVisibility(View.VISIBLE);
        hideStatus();
        if (loadDefaultPhotoNames()) {
            showNextDefaultPhoto();
            ui.removeCallbacks(advanceDefaultPhoto);
            ui.postDelayed(advanceDefaultPhoto, DEFAULT_PHOTO_DELAY_MS);
        } else {
            showStatus("Welcome!\nTap the screen, then open Settings to add a shared Google Photos or Drive link.");
        }
        overlay.setClickable(false);
        overlay.setVisibility(View.GONE);
        revealGear();
    }

    private void hideDefaultPhotos() {
        ui.removeCallbacks(advanceDefaultPhoto);
        if (defaultPhoto != null) defaultPhoto.setVisibility(View.GONE);
    }

    private boolean loadDefaultPhotoNames() {
        if (defaultPhotoNames != null) return defaultPhotoNames.length > 0;
        try {
            defaultPhotoNames = getAssets().list(DEFAULT_PHOTO_DIR);
            if (defaultPhotoNames != null) Arrays.sort(defaultPhotoNames);
            return defaultPhotoNames != null && defaultPhotoNames.length > 0;
        } catch (Exception e) {
            defaultPhotoNames = new String[0];
            return false;
        }
    }

    private void showNextDefaultPhoto() {
        if (!loadDefaultPhotoNames()) return;
        String name = defaultPhotoNames[defaultPhotoIndex % defaultPhotoNames.length];
        defaultPhotoIndex++;
        InputStream in = null;
        try {
            in = getAssets().open(DEFAULT_PHOTO_DIR + "/" + name);
            Drawable drawable = Drawable.createFromStream(in, name);
            if (drawable != null) {
                defaultPhoto.setAlpha(0f);
                defaultPhoto.setImageDrawable(drawable);
                defaultPhoto.animate().alpha(1f).setDuration(600).start();
            }
        } catch (Exception ignored) {
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) { }
        }
    }

    private void startDownload(final String url) {
        showStatus("Downloading video…");
        final File dest = new File(getFilesDir(), "remote_" + Integer.toHexString(url.hashCode()) + ".mp4");
        if (dest.exists() && dest.length() > 0) { hideStatus(); playFile(dest); return; }
        new Thread(new Runnable() {
            public void run() {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(20000);
                    c.setInstanceFollowRedirects(true);
                    c.connect();
                    InputStream in = c.getInputStream();
                    File part = new File(dest.getAbsolutePath() + ".part");
                    OutputStream os = new FileOutputStream(part);
                    byte[] b = new byte[1 << 16];
                    int n;
                    while ((n = in.read(b)) > 0) os.write(b, 0, n);
                    os.flush(); os.close(); in.close();
                    part.renameTo(dest);
                    ui.post(new Runnable() {
                        public void run() { hideStatus(); playFile(dest); }
                    });
                } catch (Exception e) {
                    ui.post(new Runnable() {
                        public void run() { showStatus("Download failed.\nTap the screen, then open Settings."); }
                    });
                }
            }
        }).start();
    }

    private boolean hasBundledVideo() {
        try {
            getAssets().openFd(ASSET_NAME).close();
            return true;
        } catch (Exception e) {
            // openFd throws for compressed assets even when present; double-check via list.
            try {
                for (String n : getAssets().list("")) if (ASSET_NAME.equals(n)) return true;
            } catch (Exception ignored) { }
            return false;
        }
    }

    /** Copy the bundled video out of assets to internal storage so VideoView can play it by path. */
    private File ensureLocalCopy() {
        if (!hasBundledVideo()) return null;
        File out = new File(getFilesDir(), ASSET_NAME);
        if (out.exists() && out.length() > 0) return out;
        try {
            InputStream in = getAssets().open(ASSET_NAME);
            OutputStream os = new FileOutputStream(out);
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            os.flush(); os.close(); in.close();
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private void revealGear() {
        overlay.setClickable(false);
        overlay.setVisibility(View.GONE);
        gear.setVisibility(View.VISIBLE);
        assistant.setVisibility(View.VISIBLE);
        gear.bringToFront();
        assistant.bringToFront();
        ui.removeCallbacks(hideGear);
        ui.postDelayed(hideGear, 5000);
    }

    private boolean controlsAreHidden() {
        boolean gearHidden = gear == null || gear.getVisibility() != View.VISIBLE;
        boolean assistantHidden = assistant == null || assistant.getVisibility() != View.VISIBLE;
        return gearHidden && assistantHidden;
    }

    private void openSettings() {
        ui.removeCallbacks(hideGear);
        startActivityForResult(new Intent(this, SettingsActivity.class), REQ_SETTINGS);
    }

    private void openAssistant() {
        ui.removeCallbacks(hideGear);
        startActivity(new Intent(this, AssistantActivity.class));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SETTINGS && resultCode == RESULT_OK) {
            hideStatus();
            video.stopPlayback();
            hideDefaultPhotos();
            loadAndPlay();
        }
    }

    private void showStatus(String msg) {
        status.setText(msg);
        status.setVisibility(View.VISIBLE);
    }

    private void hideStatus() {
        status.setVisibility(View.GONE);
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

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (albumView != null && albumView.getVisibility() == View.VISIBLE) albumView.onResume();
        else if (defaultPhoto != null && defaultPhoto.getVisibility() == View.VISIBLE) {
            ui.removeCallbacks(advanceDefaultPhoto);
            ui.postDelayed(advanceDefaultPhoto, DEFAULT_PHOTO_DELAY_MS);
        }
        else if (video != null && !video.isPlaying()) video.start();
    }

    static String getAlbumUrl(SharedPreferences p) {
        String albumUrl = p.getString(KEY_ALBUM_URL, DEFAULT_ALBUM_URL);
        if (PREVIOUS_DEFAULT_ALBUM_URL.equals(albumUrl)) {
            p.edit().putString(KEY_ALBUM_URL, DEFAULT_ALBUM_URL).apply();
            return DEFAULT_ALBUM_URL;
        }
        return albumUrl;
    }

    interface RemoteConfigCallback {
        void onComplete(boolean success, String message);
    }

    static String getOrCreateDeviceId(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, MODE_PRIVATE);
        String existing = p.getString(KEY_DEVICE_ID, "");
        if (!TextUtils.isEmpty(existing)) return existing;

        String raw = "";
        try {
            raw = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception ignored) { }
        if (TextUtils.isEmpty(raw)) raw = UUID.randomUUID().toString();

        String compact = raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.US);
        if (compact.length() > 12) compact = compact.substring(compact.length() - 12);
        if (compact.length() < 6) compact = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.US);
        String deviceId = "UP-" + compact;
        p.edit().putString(KEY_DEVICE_ID, deviceId).apply();
        return deviceId;
    }

    static String buildPairingUrl(Context context) {
        String deviceId = getOrCreateDeviceId(context);
        try {
            return DEFAULT_SETTINGS_BASE_URL + "?deviceId=" + URLEncoder.encode(deviceId, "UTF-8");
        } catch (Exception e) {
            return DEFAULT_SETTINGS_BASE_URL + "?deviceId=" + deviceId;
        }
    }

    static String buildRemoteConfigUrl(Context context) {
        String deviceId = getOrCreateDeviceId(context);
        try {
            return DEFAULT_REMOTE_CONFIG_URL + "?deviceId=" + URLEncoder.encode(deviceId, "UTF-8");
        } catch (Exception e) {
            return DEFAULT_REMOTE_CONFIG_URL + "?deviceId=" + deviceId;
        }
    }

    static void refreshRemoteConfigAsync(final Context context, final RemoteConfigCallback callback) {
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            public void run() {
                boolean success = false;
                String message = "Remote settings unavailable.";
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(buildRemoteConfigUrl(app)).openConnection();
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(10000);
                    c.setInstanceFollowRedirects(true);
                    c.connect();
                    int code = c.getResponseCode();
                    InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    String body = readText(in);
                    if (code >= 400) throw new Exception("Remote config returned HTTP " + code);
                    JSONObject root = new JSONObject(body);
                    JSONObject config = root.optJSONObject("config");
                    if (config == null) config = root;
                    applyRemoteConfig(app.getSharedPreferences(PREFS, MODE_PRIVATE), config);
                    success = true;
                    message = "Remote settings refreshed.";
                } catch (Exception e) {
                    message = e.getMessage() == null ? message : e.getMessage();
                }

                final boolean done = success;
                final String result = message;
                main.post(new Runnable() {
                    public void run() {
                        if (callback != null) callback.onComplete(done, result);
                    }
                });
            }
        }).start();
    }

    private static void applyRemoteConfig(SharedPreferences p, JSONObject config) {
        SharedPreferences.Editor editor = p.edit();
        String albumUrl = config.optString("albumUrl", config.optString("defaultAlbumUrl", ""));
        String photoHostUrl = config.optString("photoHostUrl", "");
        String assistantUrl = config.optString("assistantUrl", "");
        String mode = config.optString("mode", config.optString("defaultMode", ""));

        if (isValidWebUrl(albumUrl)) editor.putString(KEY_ALBUM_URL, albumUrl);
        if (isValidWebUrl(photoHostUrl)) editor.putString(KEY_PHOTO_HOST_URL, photoHostUrl);
        if (isValidWebUrl(assistantUrl)) editor.putString(KEY_ASSISTANT_URL, assistantUrl);
        if ("photo_host".equals(mode) || "photo-host".equals(mode)) {
            editor.putInt(KEY_MODE, MODE_PHOTO_HOST);
        } else if ("google_photos".equals(mode) || "google-photos".equals(mode)) {
            editor.putInt(KEY_MODE, MODE_GOOGLE_PHOTOS);
        }
        editor.apply();
    }

    private static boolean isValidWebUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
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

    @Override
    protected void onPause() {
        super.onPause();
        if (albumView != null) albumView.onPause();
        ui.removeCallbacks(advanceDefaultPhoto);
        if (video != null) video.pause();
    }

    @Override
    protected void onDestroy() {
        if (albumView != null) {
            albumView.destroy();
            albumView = null;
        }
        super.onDestroy();
    }
}
