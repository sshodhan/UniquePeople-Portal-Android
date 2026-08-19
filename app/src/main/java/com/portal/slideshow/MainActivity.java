package com.portal.slideshow;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {

    static final String PREFS = "slideshow_prefs";
    static final String KEY_URL = "video_url";
    static final String KEY_ALBUM_URL = "album_url";
    static final String KEY_PHOTO_HOST_URL = "photo_host_url";
    static final String KEY_MODE = "mode";
    static final String KEY_ASSISTANT_URL = "assistant_url";
    static final String KEY_MARIN_ENABLED = "marin_enabled";
    static final String KEY_DEVICE_ID = "device_id";
    static final String KEY_DEVICE_FRIENDLY_NAME = "device_friendly_name";
    static final String KEY_CURRENT_ALBUM_NAME = "current_album_name";
    static final String KEY_LAST_REMOTE_REFRESH_MS = "last_remote_refresh_ms";
    static final String KEY_CLOCK_COLOR = "clock_color";
    static final String KEY_CLOCK_TEXT_SIZE_SP = "clock_text_size_sp";
    static final String KEY_TILE_CLOCK_ENABLED = "tile_clock_enabled";
    static final String KEY_TILE_WEATHER_ENABLED = "tile_weather_enabled";
    static final String KEY_TILE_STOCKS_ENABLED = "tile_stocks_enabled";
    static final String KEY_TILE_GREETING_ENABLED = "tile_greeting_enabled";
    static final String KEY_TILE_BIRTHDAYS_ENABLED = "tile_birthdays_enabled";
    static final String KEY_TILE_RENDERER = "tile_renderer";
    static final String KEY_HOSTED_TILES_URL = "hosted_tiles_url";
    static final String KEY_WEATHER_TILE_TEXT = "weather_tile_text";
    // A hint used to invalidate the cached tile text when the household changes
    // scale. The unit actually rendered always comes from the dashboard payload
    // that carried the reading.
    static final String KEY_TEMPERATURE_UNIT = "temperature_unit";
    static final String KEY_STOCKS_TILE_TEXT = "stocks_tile_text";
    static final String KEY_LAST_DASHBOARD_REFRESH_MS = "last_dashboard_refresh_ms";
    static final String DEFAULT_SETTINGS_BASE_URL = "https://uniquepeople-web.vercel.app/settings";
    static final String DEFAULT_REMOTE_CONFIG_URL = "https://uniquepeople-web.vercel.app/api/device-config";
    static final String DEFAULT_DASHBOARD_DATA_URL = "https://uniquepeople-web.vercel.app/api/dashboard-data";
    static final String DEFAULT_HOSTED_TILES_URL = "https://uniquepeople-web.vercel.app/tiles";
    static final String DEFAULT_ALBUM_URL = "https://photos.app.goo.gl/3BjJ4L2MdXJiZQ6P6";
    static final String DEFAULT_ASSISTANT_URL = "https://uniquepeople-web.vercel.app/assistant";
    static final String DEFAULT_PHOTO_HOST_URL = "https://uniquepeople-web.vercel.app/photo-host";
    static final String DEFAULT_CLOCK_COLOR = "#39FF14";
    static final String DEFAULT_WEATHER_LOCATION = "Vienna, VA";
    static final String DEFAULT_WEATHER_TILE_TEXT = "Weather\nVienna, VA\nWaiting for data";
    static final int DEFAULT_CLOCK_TEXT_SIZE_SP = 42;
    static final int MIN_CLOCK_TEXT_SIZE_SP = 24;
    static final int MAX_CLOCK_TEXT_SIZE_SP = 72;
    static final int TILE_RAIL_WIDTH_DP = 340;
    static final String TILE_RENDERER_NATIVE = "native";
    static final String TILE_RENDERER_HOSTED = "hosted";
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
    private WebView hostedTilesView;
    private ImageView defaultPhoto;
    private TextView status;
    private LinearLayout tileRail;
    private TextView clockChrome;
    private TextView weatherTile;
    private LinearLayout stocksTile;
    private TextView greetingTile;
    private TextView birthdaysTile;
    private View overlay;
    private Button gear;
    private Button assistant;
    private String[] defaultPhotoNames;
    private int defaultPhotoIndex;
    private boolean albumZoomApplied;
    private boolean albumLoadFailed;
    private boolean hostedTilesLoaded;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
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
    private final Runnable updateClockChrome = new Runnable() {
        public void run() {
            refreshClockChrome();
            ui.postDelayed(this, 30000);
        }
    };
    private final Runnable refreshDashboardTiles = new Runnable() {
        public void run() {
            if (useHostedTiles(getSharedPreferences(PREFS, MODE_PRIVATE))) {
                refreshHostedTiles();
            } else {
                refreshDashboardDataAsync();
            }
            ui.postDelayed(this, nextDashboardRefreshDelayMs());
        }
    };

    private long nextDashboardRefreshDelayMs() {
        long base = 12 * 60 * 1000L;
        long jitter = (long) random.nextInt(7 * 60 * 1000);
        return base + jitter;
    }

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

        hostedTilesView = new WebView(this);
        configureHostedTilesView();
        hostedTilesView.setVisibility(View.GONE);
        hostedTilesView.setClickable(false);
        hostedTilesView.setFocusable(false);
        FrameLayout.LayoutParams hostedTilesParams = new FrameLayout.LayoutParams(
                dp(TILE_RAIL_WIDTH_DP + 36), FrameLayout.LayoutParams.MATCH_PARENT);
        hostedTilesParams.gravity = Gravity.TOP | Gravity.LEFT;
        root.addView(hostedTilesView, hostedTilesParams);

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(20f);
        status.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        slp.gravity = Gravity.CENTER;
        status.setVisibility(View.GONE);
        root.addView(status, slp);

        tileRail = new LinearLayout(this);
        tileRail.setOrientation(LinearLayout.VERTICAL);
        tileRail.setGravity(Gravity.LEFT);
        clockChrome = createTile(true);
        clockChrome.setBackgroundColor(Color.argb(132, 0, 0, 0));
        greetingTile = createTile(false);
        weatherTile = createTile(false);
        stocksTile = createTileGroup();
        birthdaysTile = createTile(false);
        tileRail.addView(clockChrome, tileParams(dp(8)));
        tileRail.addView(greetingTile, tileParams(dp(8)));
        tileRail.addView(weatherTile, tileParams(dp(8)));
        tileRail.addView(stocksTile, tileParams(dp(8)));
        tileRail.addView(birthdaysTile, tileParams(dp(8)));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.TOP | Gravity.LEFT;
        clp.leftMargin = dp(18);
        clp.topMargin = dp(58);
        root.addView(tileRail, clp);
        applyClockChromeSettings();
        refreshClockChrome();
        ui.postDelayed(updateClockChrome, 30000);
        ui.post(refreshDashboardTiles);

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
        loadAndPlay();
        refreshRemoteConfigAsync(this, new RemoteConfigCallback() {
            public void onComplete(boolean success, String message) {
                if (success) {
                    loadAndPlay();
                    // Config may have dropped the cached tile text because the
                    // temperature unit changed. Repaint first: clearing the
                    // preference does not touch the TextView, so without this
                    // the old reading stays on screen under its old label for
                    // the whole network round-trip. This callback is posted to
                    // the main thread, so the repaint is immediate.
                    refreshClockChrome();
                    // Then refresh through the same branch the periodic tick
                    // uses, so the tile does not sit on "waiting for data" for
                    // the 12-19 minutes until that tick lands.
                    if (useHostedTiles(getSharedPreferences(PREFS, MODE_PRIVATE))) refreshHostedTiles();
                    else refreshDashboardDataAsync();
                }
            }
        });
        AndroidUpdateManager.checkOnLaunch(this);
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
        boolean marinEnabled = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(KEY_MARIN_ENABLED, true);
        assistant.setVisibility(marinEnabled ? View.VISIBLE : View.GONE);
        if (tileRail != null) tileRail.bringToFront();
        if (hostedTilesView != null) hostedTilesView.bringToFront();
        gear.bringToFront();
        assistant.bringToFront();
        ui.removeCallbacks(hideGear);
        ui.postDelayed(hideGear, 5000);
    }

    private void refreshClockChrome() {
        if (clockChrome == null || tileRail == null) return;
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (useHostedTiles(p)) {
            tileRail.setVisibility(hostedTilesLoaded ? View.GONE : View.VISIBLE);
            if (hostedTilesView != null) hostedTilesView.setVisibility(View.VISIBLE);
        } else if (hostedTilesView != null) {
            hostedTilesView.setVisibility(View.GONE);
            hostedTilesLoaded = false;
        }
        Date now = new Date();
        String time = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(now);
        String day = new SimpleDateFormat("EEE", Locale.getDefault()).format(now);
        String date = new SimpleDateFormat("MMM d", Locale.getDefault()).format(now);
        String year = new SimpleDateFormat("yyyy", Locale.getDefault()).format(now);
        String clockText = time + "\n" + day + "  " + date + "\n" + year;
        SpannableString clockSpan = new SpannableString(clockText);
        int secondaryStart = time.length() + 1;
        clockSpan.setSpan(new RelativeSizeSpan(0.5f), secondaryStart, clockText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        clockChrome.setText(clockSpan);
        String displayName = p.getString(KEY_DEVICE_FRIENDLY_NAME, "");
        greetingTile.setText(greetingFor(now) + "\n" + (TextUtils.isEmpty(displayName) ? "UniquePeople" : displayName));
        weatherTile.setText(DEFAULT_WEATHER_TILE_TEXT);
        setStocksTileText("Stocks\nNot set");
        birthdaysTile.setText("Birthdays\nNone today");
        weatherTile.setText(p.getString(KEY_WEATHER_TILE_TEXT, DEFAULT_WEATHER_TILE_TEXT));
        setStocksTileText(p.getString(KEY_STOCKS_TILE_TEXT, "Stocks\nNot set"));

        clockChrome.setVisibility(p.getBoolean(KEY_TILE_CLOCK_ENABLED, true) ? View.VISIBLE : View.GONE);
        greetingTile.setVisibility(p.getBoolean(KEY_TILE_GREETING_ENABLED, false) ? View.VISIBLE : View.GONE);
        weatherTile.setVisibility(p.getBoolean(KEY_TILE_WEATHER_ENABLED, false) ? View.VISIBLE : View.GONE);
        stocksTile.setVisibility(p.getBoolean(KEY_TILE_STOCKS_ENABLED, false) ? View.VISIBLE : View.GONE);
        birthdaysTile.setVisibility(p.getBoolean(KEY_TILE_BIRTHDAYS_ENABLED, false) ? View.VISIBLE : View.GONE);
        boolean anyVisible = clockChrome.getVisibility() == View.VISIBLE
                || greetingTile.getVisibility() == View.VISIBLE
                || weatherTile.getVisibility() == View.VISIBLE
                || stocksTile.getVisibility() == View.VISIBLE
                || birthdaysTile.getVisibility() == View.VISIBLE;
        if (useHostedTiles(p) && hostedTilesLoaded) {
            tileRail.setVisibility(View.GONE);
        } else {
            tileRail.setVisibility(anyVisible ? View.VISIBLE : View.GONE);
        }
    }

    private void applyClockChromeSettings() {
        if (clockChrome == null) return;
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String color = p.getString(KEY_CLOCK_COLOR, DEFAULT_CLOCK_COLOR);
        int size = p.getInt(KEY_CLOCK_TEXT_SIZE_SP, DEFAULT_CLOCK_TEXT_SIZE_SP);
        if (size < MIN_CLOCK_TEXT_SIZE_SP) size = MIN_CLOCK_TEXT_SIZE_SP;
        if (size > MAX_CLOCK_TEXT_SIZE_SP) size = MAX_CLOCK_TEXT_SIZE_SP;
        int parsedColor = safeColor(color);
        clockChrome.setTextColor(parsedColor);
        clockChrome.setTextSize(size);
        styleTile(clockChrome, parsedColor);
        styleTile(greetingTile, parsedColor);
        styleTile(weatherTile, parsedColor);
        styleTile(stocksTile, parsedColor);
        styleTile(birthdaysTile, parsedColor);
    }

    private TextView createTile(boolean primary) {
        TextView tile = new TextView(this);
        tile.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tile.setGravity(Gravity.LEFT);
        tile.setTextColor(Color.parseColor(DEFAULT_CLOCK_COLOR));
        tile.setShadowLayer(12f, 0f, 3f, Color.BLACK);
        tile.setIncludeFontPadding(false);
        tile.setLineSpacing(primary ? dp(2) : dp(2), 1.0f);
        tile.setPadding(dp(12), dp(8), dp(12), dp(8));
        tile.setTextSize(primary ? DEFAULT_CLOCK_TEXT_SIZE_SP : 16f);
        return tile;
    }

    private LinearLayout.LayoutParams tileParams(int bottomMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                dp(TILE_RAIL_WIDTH_DP), LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = bottomMargin;
        return lp;
    }

    private LinearLayout createTileGroup() {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(dp(12), dp(12), dp(12), dp(12));
        return group;
    }

    private void styleTile(View tile, int accentColor) {
        if (tile == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(112, 0, 0, 0));
        bg.setStroke(dp(2), accentColor);
        bg.setCornerRadius(dp(8));
        tile.setBackground(bg);
    }

    private int safeColor(String color) {
        try {
            return Color.parseColor(color);
        } catch (Exception e) {
            return Color.parseColor(DEFAULT_CLOCK_COLOR);
        }
    }

    private String greetingFor(Date now) {
        int hour;
        try {
            hour = Integer.parseInt(new SimpleDateFormat("H", Locale.US).format(now));
        } catch (Exception e) {
            hour = 12;
        }
        if (hour < 12) return "Good morning";
        if (hour < 17) return "Good afternoon";
        return "Good evening";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean controlsAreHidden() {
        boolean gearHidden = gear == null || gear.getVisibility() != View.VISIBLE;
        boolean assistantHidden = assistant == null || assistant.getVisibility() != View.VISIBLE;
        return gearHidden && assistantHidden;
    }

    private void configureHostedTilesView() {
        WebSettings s = hostedTilesView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        hostedTilesView.setBackgroundColor(Color.TRANSPARENT);
        hostedTilesView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        hostedTilesView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                hostedTilesLoaded = true;
                refreshClockChrome();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && !request.isForMainFrame()) return;
                hostedTilesLoaded = false;
                if (hostedTilesView != null) hostedTilesView.setVisibility(View.GONE);
                refreshClockChrome();
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                if (request != null && !request.isForMainFrame()) return;
                hostedTilesLoaded = false;
                if (hostedTilesView != null) hostedTilesView.setVisibility(View.GONE);
                refreshClockChrome();
            }
        });
    }

    private void refreshHostedTiles() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!useHostedTiles(p) || hostedTilesView == null) {
            if (hostedTilesView != null) hostedTilesView.setVisibility(View.GONE);
            return;
        }
        hostedTilesView.setVisibility(View.VISIBLE);
        hostedTilesView.loadUrl(buildHostedTilesUrl(this));
    }

    private static boolean useHostedTiles(SharedPreferences p) {
        return TILE_RENDERER_HOSTED.equals(p.getString(KEY_TILE_RENDERER, TILE_RENDERER_NATIVE));
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
        if (AndroidUpdateManager.handleActivityResult(this, requestCode)) return;
        if (requestCode == REQ_SETTINGS && resultCode == RESULT_OK) {
            hideStatus();
            video.stopPlayback();
            hideDefaultPhotos();
            applyClockChromeSettings();
            refreshClockChrome();
            refreshHostedTiles();
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
        AndroidUpdateManager.resumePendingInstall(this);
        hideSystemUi();
        applyClockChromeSettings();
        refreshClockChrome();
        ui.removeCallbacks(updateClockChrome);
        ui.postDelayed(updateClockChrome, 30000);
        ui.removeCallbacks(refreshDashboardTiles);
        ui.post(refreshDashboardTiles);
        if (!controlsAreHidden()) {
            ui.removeCallbacks(hideGear);
            ui.postDelayed(hideGear, 5000);
        }
        if (albumView != null && albumView.getVisibility() == View.VISIBLE) albumView.onResume();
        else if (defaultPhoto != null && defaultPhoto.getVisibility() == View.VISIBLE) {
            ui.removeCallbacks(advanceDefaultPhoto);
            ui.postDelayed(advanceDefaultPhoto, DEFAULT_PHOTO_DELAY_MS);
        }
        else if (video != null && !video.isPlaying()) video.start();
    }

    static String getAlbumUrl(SharedPreferences p) {
        return p.getString(KEY_ALBUM_URL, DEFAULT_ALBUM_URL);
    }

    interface RemoteConfigCallback {
        void onComplete(boolean success, String message);
    }

    static String getOrCreateDeviceId(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
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
                    c.setConnectTimeout(3500);
                    c.setReadTimeout(3500);
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
        String hostedTilesUrl = config.optString("hostedTilesUrl", "");
        String mode = config.optString("mode", config.optString("defaultMode", ""));
        String displayName = config.optString("displayName", "").trim();
        String currentAlbumName = config.optString("currentAlbumName", config.optString("albumName", "")).trim();
        JSONObject dashboard = config.optJSONObject("dashboard");

        if (!TextUtils.isEmpty(displayName)) editor.putString(KEY_DEVICE_FRIENDLY_NAME, displayName);
        if (!TextUtils.isEmpty(currentAlbumName)) editor.putString(KEY_CURRENT_ALBUM_NAME, currentAlbumName);
        if (isValidWebUrl(albumUrl)) editor.putString(KEY_ALBUM_URL, albumUrl);
        if (isValidWebUrl(photoHostUrl)) editor.putString(KEY_PHOTO_HOST_URL, photoHostUrl);
        if (isValidWebUrl(assistantUrl)) editor.putString(KEY_ASSISTANT_URL, assistantUrl);
        if (config.has("marinEnabled")) editor.putBoolean(KEY_MARIN_ENABLED, config.optBoolean("marinEnabled", true));
        if (isValidWebUrl(hostedTilesUrl)) editor.putString(KEY_HOSTED_TILES_URL, hostedTilesUrl);
        if (dashboard != null) {
            String tileRenderer = dashboard.optString("tileRenderer", "");
            if (TILE_RENDERER_HOSTED.equals(tileRenderer)) {
                editor.putString(KEY_TILE_RENDERER, TILE_RENDERER_HOSTED);
            } else if (TILE_RENDERER_NATIVE.equals(tileRenderer)) {
                editor.putString(KEY_TILE_RENDERER, TILE_RENDERER_NATIVE);
            }
            // Only when the server actually stated a unit. An absent field
            // means "older server", not "prefers Fahrenheit" — persisting a
            // preference the server never sent would be a guess stored as
            // truth (LEARNINGS.md §1).
            String temperatureUnit = dashboard.optString("temperatureUnit", "");
            if ("C".equals(temperatureUnit) || "F".equals(temperatureUnit)) {
                if (!temperatureUnit.equals(p.getString(KEY_TEMPERATURE_UNIT, "F"))) {
                    // The cached tile text is already formatted, so it would
                    // otherwise keep showing the old scale until the next
                    // dashboard tick. Drop it and show "waiting" instead of a
                    // confidently wrong reading.
                    editor.remove(KEY_WEATHER_TILE_TEXT);
                }
                editor.putString(KEY_TEMPERATURE_UNIT, temperatureUnit);
            }
        }
        if ("photo_host".equals(mode) || "photo-host".equals(mode)) {
            editor.putInt(KEY_MODE, MODE_PHOTO_HOST);
        } else if ("google_photos".equals(mode) || "google-photos".equals(mode)) {
            editor.putInt(KEY_MODE, MODE_GOOGLE_PHOTOS);
        }
        editor.putLong(KEY_LAST_REMOTE_REFRESH_MS, System.currentTimeMillis());
        editor.apply();
    }

    // Dashboard refreshes can overlap — the periodic tick, onResume, and the
    // remote-config callback all start one. Without a token, an older response
    // landing last would overwrite both the cached string and the visible tile,
    // and after a unit change that means the wrong scale sticks until the next
    // 12-19 minute tick. Strictly increasing, compared for equality, so only
    // the newest request may apply its result.
    private int dashboardRefreshGeneration = 0;

    private void refreshDashboardDataAsync() {
        final Context app = getApplicationContext();
        final int generation = ++dashboardRefreshGeneration;
        new Thread(new Runnable() {
            public void run() {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(buildDashboardDataUrl(app)).openConnection();
                    c.setConnectTimeout(3500);
                    c.setReadTimeout(5000);
                    c.setInstanceFollowRedirects(true);
                    c.connect();
                    int code = c.getResponseCode();
                    InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    String body = readText(in);
                    if (code >= 400) throw new Exception("Dashboard returned HTTP " + code);
                    JSONObject root = new JSONObject(body);
                    // A server that predates this field yields "F", which is
                    // exactly today's behavior (LEARNINGS.md §1).
                    final String weatherText = formatWeatherTile(root.optJSONObject("weather"),
                            root.optString("temperatureUnit", "F"));
                    final String stocksText = formatStocksTile(root.optJSONObject("sp500"), root.optJSONArray("stocks"), root.optJSONObject("status"));
                    ui.post(new Runnable() {
                        public void run() {
                            // Generation is owned by the UI thread, so this
                            // check and the writes below cannot interleave with
                            // another response.
                            if (generation != dashboardRefreshGeneration) return;
                            app.getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                    .putString(KEY_WEATHER_TILE_TEXT, weatherText)
                                    .putString(KEY_STOCKS_TILE_TEXT, stocksText)
                                    .putLong(KEY_LAST_DASHBOARD_REFRESH_MS, System.currentTimeMillis())
                                    .apply();
                            if (weatherTile != null) weatherTile.setText(weatherText);
                            setStocksTileText(stocksText);
                            refreshClockChrome();
                        }
                    });
                } catch (Exception ignored) {
                    ui.post(new Runnable() {
                        public void run() {
                            if (generation != dashboardRefreshGeneration) return;
                            refreshClockChrome();
                        }
                    });
                }
            }
        }).start();
    }

    static String buildDashboardDataUrl(Context context) {
        String deviceId = getOrCreateDeviceId(context);
        try {
            return DEFAULT_DASHBOARD_DATA_URL + "?deviceId=" + URLEncoder.encode(deviceId, "UTF-8");
        } catch (Exception e) {
            return DEFAULT_DASHBOARD_DATA_URL + "?deviceId=" + deviceId;
        }
    }

    static String buildHostedTilesUrl(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, MODE_PRIVATE);
        String base = p.getString(KEY_HOSTED_TILES_URL, DEFAULT_HOSTED_TILES_URL);
        String deviceId = getOrCreateDeviceId(context);
        String accent = p.getString(KEY_CLOCK_COLOR, DEFAULT_CLOCK_COLOR);
        String displayName = p.getString(KEY_DEVICE_FRIENDLY_NAME, "");
        try {
            String separator = base.contains("?") ? "&" : "?";
            return base
                    + separator + "deviceId=" + URLEncoder.encode(deviceId, "UTF-8")
                    + "&accent=" + URLEncoder.encode(accent, "UTF-8")
                    + "&name=" + URLEncoder.encode(displayName, "UTF-8");
        } catch (Exception e) {
            return base;
        }
    }

    // The server always sends Fahrenheit readings; temperatureUnit carries the
    // household's display preference alongside them. Both come out of the same
    // JSON object so the cached tile string can never mix a number from one
    // fetch with a unit from another.
    private static String formatWeatherTile(JSONObject weather, String temperatureUnit) {
        if (weather == null || !weather.optBoolean("enabled", false)) return DEFAULT_WEATHER_TILE_TEXT;
        boolean celsius = "C".equalsIgnoreCase(temperatureUnit);
        String condition = weather.optString("condition", "Weather").toUpperCase(Locale.US);
        String icon = weather.optString("icon", "");
        int temp = weather.optInt("temperatureF", Integer.MIN_VALUE);
        int high = weather.optInt("highF", Integer.MIN_VALUE);
        int low = weather.optInt("lowF", Integer.MIN_VALUE);
        String warning = weather.optString("warning",
                weather.optString("alert", ""));
        StringBuilder out = new StringBuilder();
        out.append(TextUtils.isEmpty(icon) ? "Weather" : icon).append(" ").append(condition);
        if (temp != Integer.MIN_VALUE) {
            out.append("  ").append(toDisplayTemperature(temp, celsius)).append(celsius ? "°C" : "°F");
        }
        if (high != Integer.MIN_VALUE && low != Integer.MIN_VALUE) {
            out.append("\nH ").append(toDisplayTemperature(high, celsius))
                    .append("  L ").append(toDisplayTemperature(low, celsius));
        }
        out.append("\n").append(TextUtils.isEmpty(warning) ? "No alerts" : warning);
        return out.toString();
    }

    // Mirrors the web rule in public/tile-models.js so both renderers agree.
    // 5.0 / 9.0 is deliberate: integer division would return 0 and flatten
    // every Celsius reading to 0.
    private static int toDisplayTemperature(int fahrenheit, boolean celsius) {
        return celsius ? (int) Math.round((fahrenheit - 32) * 5.0 / 9.0) : fahrenheit;
    }

    private static String formatStocksTile(JSONObject sp500, JSONArray stocks, JSONObject status) {
        StringBuilder out = new StringBuilder();
        out.append("STOCKS");
        String syncedLabel = formatSyncedLabel(status);
        if (!TextUtils.isEmpty(syncedLabel)) out.append(" · ").append(syncedLabel);
        appendQuote(out, sp500, "S&P 500");
        if (stocks != null) {
            for (int i = 0; i < stocks.length(); i++) {
                JSONObject quote = stocks.optJSONObject(i);
                if (quote != null) appendQuote(out, quote, "");
            }
        }
        if (out.length() == 0) out.append("Stocks\nNot set");
        return out.toString();
    }

    private static String formatSyncedLabel(JSONObject status) {
        if (status == null) return "";
        String prefix = status.optBoolean("cached", false) ? "Cached" : "Synced";
        String updatedAt = status.optString("updatedAt", "");
        String time = formatIsoTime(updatedAt);
        return TextUtils.isEmpty(time) ? prefix : prefix + " " + time;
    }

    private static String formatIsoTime(String updatedAt) {
        if (TextUtils.isEmpty(updatedAt)) return "";
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            input.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = input.parse(updatedAt);
            if (date == null) return "";
            return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(date);
        } catch (Exception ignored) {
            return "";
        }
    }

    private void setStocksTileText(String text) {
        if (stocksTile == null) return;
        stocksTile.removeAllViews();
        if (TextUtils.isEmpty(text)) {
            addStocksHeader("Stocks\nNot set");
            return;
        }
        String[] lines = text.split("\\n");
        if (lines.length == 0) {
            addStocksHeader(text);
            return;
        }
        addStocksHeader(lines[0]);
        if (lines.length > 1) {
            stocksTile.addView(buildStockCard(lines[1], true), stockSubTileParams());
        }
        int stockCount = Math.max(0, lines.length - 2);
        if (stockCount > 0) {
            LinearLayout chipRow = new LinearLayout(this);
            chipRow.setOrientation(LinearLayout.HORIZONTAL);
            int start = (int) ((System.currentTimeMillis() / 30000L) % stockCount);
            int visible = Math.min(2, stockCount);
            for (int i = 0; i < visible; i++) {
                int lineIndex = 2 + ((start + i) % stockCount);
                LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (i > 0) chipParams.leftMargin = dp(5);
                chipRow.addView(buildStockCard(lines[lineIndex], false), chipParams);
            }
            stocksTile.addView(chipRow, stockSubTileParams());
        }
    }

    private void addStocksHeader(String text) {
        TextView header = new TextView(this);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setTextColor(safeColor(getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_CLOCK_COLOR, DEFAULT_CLOCK_COLOR)));
        header.setTextSize(13f);
        header.setIncludeFontPadding(false);
        header.setText(text);
        stocksTile.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private View buildStockCard(String line, boolean featured) {
        String[] parts = line.split("\\|", -1);
        if (parts.length < 4) {
            TextView fallback = createTile(false);
            fallback.setText(line);
            return fallback;
        }
        String symbol = parts[0];
        String price = parts[1];
        String percent = parts[2];
        String direction = parts[3];
        String sparkline = parts.length > 4 ? parts[4] : "";
        int accent = safeColor(getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_CLOCK_COLOR, DEFAULT_CLOCK_COLOR));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(featured ? dp(14) : dp(10), featured ? dp(14) : dp(10), featured ? dp(14) : dp(10), featured ? dp(14) : dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(132, 8, 10, 18));
        bg.setStroke(dp(1), Color.argb(180, Color.red(accent), Color.green(accent), Color.blue(accent)));
        bg.setCornerRadius(dp(6));
        card.setBackground(bg);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = stockText(featured ? symbol : symbol.replace("^", ""), featured ? 16f : 12f, Color.LTGRAY);
        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView pill = stockText("GOOD", featured ? 12f : 10f, accent);
        pill.setGravity(Gravity.CENTER);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(Color.argb(72, Color.red(accent), Color.green(accent), Color.blue(accent)));
        pillBg.setStroke(dp(1), accent);
        pillBg.setCornerRadius(dp(5));
        pill.setBackground(pillBg);
        pill.setPadding(dp(8), dp(3), dp(8), dp(3));
        top.addView(pill, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        TextView priceView = stockText(compactPrice(price), featured ? 34f : 18f, Color.WHITE);
        bottom.addView(priceView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        String arrow = "down".equals(direction) ? "▼ " : ("flat".equals(direction) ? "" : "▲ ");
        int trendColor = "down".equals(direction) ? Color.rgb(255, 83, 112) : accent;
        TextView trend = stockText(arrow + percent, featured ? 22f : 14f, trendColor);
        bottom.addView(trend, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        card.addView(top);
        card.addView(bottom);
        card.addView(new StockSparklineView(this, sparkline, trendColor), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, featured ? dp(42) : dp(26)));
        return card;
    }

    private LinearLayout.LayoutParams stockSubTileParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        return params;
    }

    private TextView stockText(String text, float sizeSp, int color) {
        TextView view = new TextView(this);
        view.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        view.setIncludeFontPadding(false);
        view.setTextColor(color);
        view.setTextSize(sizeSp);
        view.setText(text);
        return view;
    }

    private static String compactPrice(String price) {
        if (TextUtils.isEmpty(price) || "--".equals(price)) return price;
        int dot = price.indexOf('.');
        return dot > 0 ? price.substring(0, dot) : price;
    }

    private static void appendQuote(StringBuilder out, JSONObject quote, String label) {
        if (quote == null) return;
        String symbol = quote.optString("symbol", quote.optString("label", "")).trim();
        if (TextUtils.isEmpty(symbol)) symbol = label;
        String percent = quote.optString("dayReturnPercent", "--");
        String price = quote.optString("price", "");
        String direction = quote.optString("direction", "flat");
        String sparkline = encodeMonthSparkline(quote.optJSONObject("sparkline"));
        if (TextUtils.isEmpty(symbol)) return;
        if (!TextUtils.isEmpty(price) && !price.startsWith("$")) price = "$" + price;
        if (out.length() > 0) out.append("\n");
        out.append(symbol).append("|").append(price).append("|").append(percent).append("|").append(direction).append("|").append(sparkline);
    }

    private static String encodeMonthSparkline(JSONObject sparkline) {
        if (sparkline == null) return "";
        JSONArray month = sparkline.optJSONArray("month");
        if (month == null || month.length() == 0) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < month.length(); i++) {
            if (i > 0) out.append(",");
            out.append(month.optInt(i));
        }
        return out.toString();
    }

    private static final class StockSparklineView extends View {
        private final float[] points;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        StockSparklineView(Context context, String csv, int color) {
            super(context);
            points = parsePoints(csv);
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (points.length < 2) return;
            float width = getWidth() - getPaddingLeft() - getPaddingRight();
            float height = getHeight() - getPaddingTop() - getPaddingBottom();
            if (width <= 0 || height <= 0) return;
            paint.setStrokeWidth(Math.max(2f, height / 10f));
            Path path = new Path();
            for (int i = 0; i < points.length; i++) {
                float x = getPaddingLeft() + (width * i / (points.length - 1));
                float y = getPaddingTop() + height - (height * points[i] / 100f);
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            canvas.drawPath(path, paint);
        }

        private static float[] parsePoints(String csv) {
            if (TextUtils.isEmpty(csv)) return new float[0];
            String[] parts = csv.split(",");
            float[] parsed = new float[parts.length];
            int count = 0;
            for (String part : parts) {
                try {
                    float value = Float.parseFloat(part.trim());
                    if (value < 0f) value = 0f;
                    if (value > 100f) value = 100f;
                    parsed[count++] = value;
                } catch (Exception ignored) { }
            }
            return count == parsed.length ? parsed : Arrays.copyOf(parsed, count);
        }
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
        ui.removeCallbacks(updateClockChrome);
        ui.removeCallbacks(refreshDashboardTiles);
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
