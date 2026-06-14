package com.portal.slideshow;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    static final String PREFS = "slideshow_prefs";
    static final String KEY_URL = "video_url";
    static final String KEY_MODE = "mode";
    static final String KEY_ASSISTANT_URL = "assistant_url";
    static final String DEFAULT_ASSISTANT_URL = "http://10.0.2.2:3000";
    static final int MODE_BUNDLED = 0;
    static final int MODE_STREAM = 1;
    static final int MODE_DOWNLOAD = 2;
    private static final String ASSET_NAME = "slideshow.mp4";
    private static final int REQ_SETTINGS = 100;

    private VideoView video;
    private TextView status;
    private Button gear;
    private Button assistant;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable hideGear = new Runnable() {
        public void run() {
            if (gear != null) gear.setVisibility(View.GONE);
            if (assistant != null) assistant.setVisibility(View.GONE);
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

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(20f);
        status.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        slp.gravity = Gravity.CENTER;
        status.setVisibility(View.GONE);
        root.addView(status, slp);

        // Transparent tap-catcher to reveal the settings button.
        View overlay = new View(this);
        overlay.setClickable(true);
        overlay.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { revealGear(); }
        });
        root.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        gear = new Button(this);
        gear.setText("⚙  Settings");
        gear.setTextSize(18f);
        gear.setVisibility(View.GONE);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        glp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
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

        loadAndPlay();
    }

    private void loadAndPlay() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String url = p.getString(KEY_URL, "");
        int mode = p.getInt(KEY_MODE, hasBundledVideo() ? MODE_BUNDLED : MODE_STREAM);

        if (mode == MODE_BUNDLED || TextUtils.isEmpty(url)) {
            File f = ensureLocalCopy();
            if (f != null) {
                playFile(f);
            } else {
                showStatus("Welcome!\nTap the screen, then open Settings to add a video URL.");
                revealGear();
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
        video.setVideoURI(Uri.fromFile(f));
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
        gear.setVisibility(View.VISIBLE);
        assistant.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideGear);
        ui.postDelayed(hideGear, 5000);
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
        if (video != null && !video.isPlaying()) video.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (video != null) video.pause();
    }
}
