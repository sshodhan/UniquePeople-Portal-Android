package com.portal.slideshow;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.text.DateFormat;
import java.util.Date;

public class SettingsActivity extends Activity {

    private static final int REQ_QR_SCAN = 42;

    private EditText urlField;
    private EditText albumUrlField;
    private EditText photoHostUrlField;
    private EditText assistantUrlField;
    private EditText hostedTilesUrlField;
    private EditText pairingUrlField;
    private EditText displayNameField;
    private EditText clockSizeField;
    private ImageView pairingQr;
    private RadioButton rPhotoHost, rGooglePhotos, rStream, rDownload, rBundled;
    private RadioButton rTileNative, rTileHosted;
    private RadioButton rClockGreen, rClockWhite, rClockAmber, rClockCyan;
    private CheckBox tileClock, tileWeather, tileStocks, tileGreeting, tileBirthdays;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("UniquePeople V2 Settings");

        SharedPreferences p = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String url = p.getString(MainActivity.KEY_URL, "");
        String albumUrl = MainActivity.getAlbumUrl(p);
        String photoHostUrl = p.getString(MainActivity.KEY_PHOTO_HOST_URL, MainActivity.DEFAULT_PHOTO_HOST_URL);
        String assistantUrl = p.getString(MainActivity.KEY_ASSISTANT_URL, MainActivity.DEFAULT_ASSISTANT_URL);
        String hostedTilesUrl = p.getString(MainActivity.KEY_HOSTED_TILES_URL, MainActivity.DEFAULT_HOSTED_TILES_URL);
        String displayName = p.getString(MainActivity.KEY_DEVICE_FRIENDLY_NAME, "");
        String clockColor = p.getString(MainActivity.KEY_CLOCK_COLOR, MainActivity.DEFAULT_CLOCK_COLOR);
        int clockSize = p.getInt(MainActivity.KEY_CLOCK_TEXT_SIZE_SP, MainActivity.DEFAULT_CLOCK_TEXT_SIZE_SP);
        boolean showClock = p.getBoolean(MainActivity.KEY_TILE_CLOCK_ENABLED, true);
        boolean showWeather = p.getBoolean(MainActivity.KEY_TILE_WEATHER_ENABLED, false);
        boolean showStocks = p.getBoolean(MainActivity.KEY_TILE_STOCKS_ENABLED, false);
        boolean showGreeting = p.getBoolean(MainActivity.KEY_TILE_GREETING_ENABLED, false);
        boolean showBirthdays = p.getBoolean(MainActivity.KEY_TILE_BIRTHDAYS_ENABLED, false);
        String tileRenderer = p.getString(MainActivity.KEY_TILE_RENDERER, MainActivity.TILE_RENDERER_NATIVE);
        int mode = p.getInt(MainActivity.KEY_MODE, MainActivity.MODE_GOOGLE_PHOTOS);
        boolean hasBundled = hasBundledVideo();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#10131A"));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(28);
        col.setPadding(pad, pad, pad, pad);
        scroll.addView(col);

        final String deviceId = MainActivity.getOrCreateDeviceId(this);
        final String pairingUrl = MainActivity.buildPairingUrl(this);

        col.addView(title("UniquePeople V2 Settings"));
        col.addView(label("V2 setup for this Portal. Start with your phone, then refresh here and save."));

        LinearLayout phoneSetup = addExpandableSection(col, "1. Use Your Phone",
                "Scan the QR code with your phone, choose the album on the web page, then tap Refresh from Web here.",
                true);
        phoneSetup.addView(help("Best for family setup. The QR code opens the web companion for only this Portal."));
        pairingQr = new ImageView(this);
        pairingQr.setBackgroundColor(Color.WHITE);
        pairingQr.setPadding(dp(10), dp(10), dp(10), dp(10));
        pairingQr.setAdjustViewBounds(true);
        phoneSetup.addView(pairingQr, imageBox(dp(8)));
        loadPairingQr(pairingUrl);

        phoneSetup.addView(fieldLabel("Portal device ID"));
        phoneSetup.addView(readOnlyValue(deviceId), wide(dp(4)));
        phoneSetup.addView(help("This ID is unique to this Portal. Web settings saved for other Portals will not affect this one."));
        phoneSetup.addView(fieldLabel("Friendly device name"));
        displayNameField = readOnlyValue(TextUtils.isEmpty(displayName) ? "Not set yet" : displayName);
        phoneSetup.addView(displayNameField, wide(dp(4)));
        phoneSetup.addView(help("Set this on the web companion so each family Portal is easy to recognize."));
        phoneSetup.addView(help(lastRemoteRefreshText(p)));

        phoneSetup.addView(fieldLabel("Phone setup link"));
        pairingUrlField = readOnlyValue(pairingUrl);
        phoneSetup.addView(pairingUrlField, wide(dp(4)));

        Button copyPairing = bigButton("Copy phone setup link", "#00796B");
        copyPairing.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("UniquePeople setup link", pairingUrl));
                Toast.makeText(SettingsActivity.this, "Pairing link copied.", Toast.LENGTH_SHORT).show();
            }
        });
        phoneSetup.addView(copyPairing, wide(dp(8)));

        Button refreshRemote = bigButton("Refresh settings from web", "#2F6BFF");
        refreshRemote.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                refreshRemote.setEnabled(false);
                MainActivity.refreshRemoteConfigAsync(SettingsActivity.this, new MainActivity.RemoteConfigCallback() {
                    public void onComplete(boolean success, String message) {
                        refreshRemote.setEnabled(true);
                        reloadFieldsFromPrefs();
                        Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
        phoneSetup.addView(refreshRemote, wide(dp(12)));

        LinearLayout albumSetup = addExpandableSection(col, "2. Scan Album QR or Paste Link",
                "Use the Portal camera to scan a Google Photos album QR code, or paste the shared album link directly.",
                false);
        albumSetup.addView(fieldLabel("Shared Google Photos or Drive link"));
        albumSetup.addView(help("Optional. Paste a public/shared album or folder link. This path works even without the web companion."));
        albumUrlField = new EditText(this);
        albumUrlField.setHint("https://photos.app.goo.gl/... or https://drive.google.com/...");
        albumUrlField.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        albumUrlField.setText(albumUrl);
        albumUrlField.setTextColor(Color.WHITE);
        albumUrlField.setHintTextColor(Color.parseColor("#7A8090"));
        albumUrlField.setTextSize(18f);
        albumUrlField.setMinHeight(dp(64));
        albumSetup.addView(albumUrlField, wide(dp(4)));

        Button scanQr = bigButton("Scan Album QR Code", "#00796B");
        scanQr.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivityForResult(new Intent(SettingsActivity.this, QrScanActivity.class), REQ_QR_SCAN);
            }
        });
        albumSetup.addView(scanQr, wide(dp(10)));

        LinearLayout display = addExpandableSection(col, "Display",
                "Adjust the tile rail that appears over the photos.",
                false);
        addClockDisplaySettings(display, clockColor, clockSize,
                showClock, showWeather, showStocks, showGreeting, showBirthdays,
                tileRenderer, hostedTilesUrl);

        LinearLayout advanced = addExpandableSection(col, "Advanced",
                "Website viewer, assistant, and legacy video fallback options.",
                false);

        Button openPairing = bigButton("Open setup page on this Portal", "#33394A");
        openPairing.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(pairingUrl)));
            }
        });
        advanced.addView(openPairing, wide(dp(8)));

        advanced.addView(sectionTitle("Display Mode"));
        advanced.addView(help("Direct shared-link mode is best for most people. Use Photo Host only if you have a separate website that renders the album."));
        RadioGroup group = new RadioGroup(this);
        rGooglePhotos = radio("Open shared Google Photos or Drive link directly");
        rPhotoHost = radio("Load through Photo Host viewer (advanced)");
        rStream = radio("Stream a video URL");
        rDownload = radio("Download a video once, then play offline");
        group.addView(rGooglePhotos);
        group.addView(rPhotoHost);
        group.addView(rStream);
        group.addView(rDownload);
        if (hasBundled) {
            rBundled = radio("Use the built-in video");
            group.addView(rBundled);
        }
        advanced.addView(group, wide(dp(8)));

        advanced.addView(sectionTitle("Advanced Website Viewer"));
        advanced.addView(fieldLabel("Photo Host viewer URL"));
        advanced.addView(help("Optional. Only used when Photo Host mode is selected. The app opens this URL with albumUrl=<your shared link>."));
        photoHostUrlField = new EditText(this);
        photoHostUrlField.setHint(MainActivity.DEFAULT_PHOTO_HOST_URL);
        photoHostUrlField.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        photoHostUrlField.setText(photoHostUrl);
        photoHostUrlField.setTextColor(Color.WHITE);
        photoHostUrlField.setHintTextColor(Color.parseColor("#7A8090"));
        photoHostUrlField.setTextSize(18f);
        photoHostUrlField.setMinHeight(dp(64));
        advanced.addView(photoHostUrlField, wide(dp(4)));

        advanced.addView(sectionTitle("Video Fallback"));
        advanced.addView(fieldLabel("Video URL"));
        advanced.addView(help("Optional. Used only for Stream or Download video modes. Leave blank for photo modes and default photos."));
        urlField = new EditText(this);
        urlField.setHint("https://example.com/family.mp4");
        urlField.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        urlField.setText(url);
        urlField.setTextColor(Color.WHITE);
        urlField.setHintTextColor(Color.parseColor("#7A8090"));
        urlField.setTextSize(18f);
        urlField.setMinHeight(dp(64));
        advanced.addView(urlField, wide(dp(4)));

        if (mode == MainActivity.MODE_GOOGLE_PHOTOS) rGooglePhotos.setChecked(true);
        else if (mode == MainActivity.MODE_PHOTO_HOST) rPhotoHost.setChecked(true);
        else if (mode == MainActivity.MODE_DOWNLOAD) rDownload.setChecked(true);
        else if (mode == MainActivity.MODE_BUNDLED && hasBundled) rBundled.setChecked(true);
        else rStream.setChecked(true);

        advanced.addView(sectionTitle("Portal Assistant"));
        advanced.addView(fieldLabel("Assistant web app URL"));
        advanced.addView(help("Optional. This should point to the browser assistant app. API keys stay on that server, not inside this Android APK."));

        assistantUrlField = new EditText(this);
        assistantUrlField.setHint(MainActivity.DEFAULT_ASSISTANT_URL);
        assistantUrlField.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        assistantUrlField.setText(assistantUrl);
        assistantUrlField.setTextColor(Color.WHITE);
        assistantUrlField.setHintTextColor(Color.parseColor("#7A8090"));
        assistantUrlField.setTextSize(18f);
        assistantUrlField.setMinHeight(dp(64));
        advanced.addView(assistantUrlField, wide(dp(4)));

        Button openAssistant = bigButton("Open Assistant", "#00796B");
        openAssistant.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (saveAssistantUrl()) {
                    startActivity(new android.content.Intent(SettingsActivity.this, AssistantActivity.class));
                }
            }
        });
        advanced.addView(openAssistant, wide(dp(16)));

        Button save = bigButton("Save & Play", "#2F6BFF");
        save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { save(); }
        });
        col.addView(save, wide(dp(24)));

        Button cancel = bigButton("Cancel", "#33394A");
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        col.addView(cancel, wide(dp(12)));
        col.addView(spacer(dp(64)));

        setContentView(scroll);
    }

    private void reloadFieldsFromPrefs() {
        SharedPreferences p = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        albumUrlField.setText(MainActivity.getAlbumUrl(p));
        photoHostUrlField.setText(p.getString(MainActivity.KEY_PHOTO_HOST_URL, MainActivity.DEFAULT_PHOTO_HOST_URL));
        assistantUrlField.setText(p.getString(MainActivity.KEY_ASSISTANT_URL, MainActivity.DEFAULT_ASSISTANT_URL));
        if (hostedTilesUrlField != null) {
            hostedTilesUrlField.setText(p.getString(MainActivity.KEY_HOSTED_TILES_URL, MainActivity.DEFAULT_HOSTED_TILES_URL));
        }
        if (displayNameField != null) {
            String displayName = p.getString(MainActivity.KEY_DEVICE_FRIENDLY_NAME, "");
            displayNameField.setText(TextUtils.isEmpty(displayName) ? "Not set yet" : displayName);
        }
        if (clockSizeField != null) {
            clockSizeField.setText(String.valueOf(p.getInt(MainActivity.KEY_CLOCK_TEXT_SIZE_SP, MainActivity.DEFAULT_CLOCK_TEXT_SIZE_SP)));
        }
        String tileRenderer = p.getString(MainActivity.KEY_TILE_RENDERER, MainActivity.TILE_RENDERER_NATIVE);
        if (rTileHosted != null && MainActivity.TILE_RENDERER_HOSTED.equals(tileRenderer)) rTileHosted.setChecked(true);
        else if (rTileNative != null) rTileNative.setChecked(true);
        String clockColor = p.getString(MainActivity.KEY_CLOCK_COLOR, MainActivity.DEFAULT_CLOCK_COLOR);
        if (rClockWhite != null && "#FFFFFF".equalsIgnoreCase(clockColor)) rClockWhite.setChecked(true);
        else if (rClockAmber != null && "#FFB000".equalsIgnoreCase(clockColor)) rClockAmber.setChecked(true);
        else if (rClockCyan != null && "#00E5FF".equalsIgnoreCase(clockColor)) rClockCyan.setChecked(true);
        else if (rClockGreen != null) rClockGreen.setChecked(true);
        if (tileClock != null) tileClock.setChecked(p.getBoolean(MainActivity.KEY_TILE_CLOCK_ENABLED, true));
        if (tileWeather != null) tileWeather.setChecked(p.getBoolean(MainActivity.KEY_TILE_WEATHER_ENABLED, false));
        if (tileStocks != null) tileStocks.setChecked(p.getBoolean(MainActivity.KEY_TILE_STOCKS_ENABLED, false));
        if (tileGreeting != null) tileGreeting.setChecked(p.getBoolean(MainActivity.KEY_TILE_GREETING_ENABLED, false));
        if (tileBirthdays != null) tileBirthdays.setChecked(p.getBoolean(MainActivity.KEY_TILE_BIRTHDAYS_ENABLED, false));
        int mode = p.getInt(MainActivity.KEY_MODE, MainActivity.MODE_GOOGLE_PHOTOS);
        if (mode == MainActivity.MODE_PHOTO_HOST) rPhotoHost.setChecked(true);
        else rGooglePhotos.setChecked(true);
    }

    private void addClockDisplaySettings(LinearLayout col, String clockColor, int clockSize,
                                         boolean showClock, boolean showWeather, boolean showStocks,
                                         boolean showGreeting, boolean showBirthdays,
                                         String tileRenderer, String hostedTilesUrl) {
        col.addView(sectionTitle("Tile Rail"));
        col.addView(help("Shown as compact stacked tiles on the left side of the slideshow. Optional tiles can stay hidden until their data is ready."));
        col.addView(fieldLabel("Tile renderer"));
        col.addView(help("Native tiles are the stable local fallback. Hosted web tiles are V3 and can be updated from Vercel without rebuilding the APK."));
        RadioGroup tileRendererGroup = new RadioGroup(this);
        rTileNative = radio("Native Portal tiles");
        rTileHosted = radio("Hosted web tiles (V3)");
        tileRendererGroup.addView(rTileNative);
        tileRendererGroup.addView(rTileHosted);
        col.addView(tileRendererGroup, wide(dp(4)));
        if (MainActivity.TILE_RENDERER_HOSTED.equals(tileRenderer)) rTileHosted.setChecked(true);
        else rTileNative.setChecked(true);

        col.addView(fieldLabel("Hosted tiles URL"));
        hostedTilesUrlField = new EditText(this);
        hostedTilesUrlField.setHint(MainActivity.DEFAULT_HOSTED_TILES_URL);
        hostedTilesUrlField.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        hostedTilesUrlField.setText(hostedTilesUrl);
        hostedTilesUrlField.setTextColor(Color.WHITE);
        hostedTilesUrlField.setHintTextColor(Color.parseColor("#7A8090"));
        hostedTilesUrlField.setTextSize(18f);
        hostedTilesUrlField.setMinHeight(dp(64));
        col.addView(hostedTilesUrlField, wide(dp(12)));

        tileClock = checkbox("Clock", showClock);
        tileGreeting = checkbox("Daily greeting", showGreeting);
        tileWeather = checkbox("Weather", showWeather);
        tileStocks = checkbox("Stocks", showStocks);
        tileBirthdays = checkbox("Birthday reminders", showBirthdays);
        col.addView(tileClock);
        col.addView(tileGreeting);
        col.addView(tileWeather);
        col.addView(tileStocks);
        col.addView(tileBirthdays);

        col.addView(sectionTitle("Clock Display"));
        col.addView(help("Green is the default because it is easiest to read across the room. The clock tile keeps time, day, date, and year stacked."));
        col.addView(fieldLabel("Clock color"));
        RadioGroup clockColorGroup = new RadioGroup(this);
        rClockGreen = radio("Classic digital green");
        rClockWhite = radio("White");
        rClockAmber = radio("Amber");
        rClockCyan = radio("Cyan");
        clockColorGroup.addView(rClockGreen);
        clockColorGroup.addView(rClockWhite);
        clockColorGroup.addView(rClockAmber);
        clockColorGroup.addView(rClockCyan);
        col.addView(clockColorGroup, wide(dp(4)));
        if ("#FFFFFF".equalsIgnoreCase(clockColor)) rClockWhite.setChecked(true);
        else if ("#FFB000".equalsIgnoreCase(clockColor)) rClockAmber.setChecked(true);
        else if ("#00E5FF".equalsIgnoreCase(clockColor)) rClockCyan.setChecked(true);
        else rClockGreen.setChecked(true);

        col.addView(fieldLabel("Clock font size"));
        col.addView(help("Use a number from 24 to 72. Larger sizes are easier to see across the room."));
        clockSizeField = new EditText(this);
        clockSizeField.setHint(String.valueOf(MainActivity.DEFAULT_CLOCK_TEXT_SIZE_SP));
        clockSizeField.setInputType(InputType.TYPE_CLASS_NUMBER);
        clockSizeField.setText(String.valueOf(clockSize));
        clockSizeField.setTextColor(Color.WHITE);
        clockSizeField.setHintTextColor(Color.parseColor("#7A8090"));
        clockSizeField.setTextSize(18f);
        clockSizeField.setMinHeight(dp(64));
        col.addView(clockSizeField, wide(dp(12)));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_QR_SCAN && resultCode == RESULT_OK) {
            String albumUrl = data != null ? data.getStringExtra("album_url") : null;
            if (TextUtils.isEmpty(albumUrl)) {
                albumUrl = MainActivity.getAlbumUrl(getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE));
            }
            albumUrlField.setText(albumUrl);
            rGooglePhotos.setChecked(true);
            Toast.makeText(this, "QR link saved. Tap Save & Play to return to the slideshow.", Toast.LENGTH_LONG).show();
        }
    }

    private void save() {
        int mode;
        if (rBundled != null && rBundled.isChecked()) {
            mode = MainActivity.MODE_BUNDLED;
        } else if (rDownload.isChecked()) {
            mode = MainActivity.MODE_DOWNLOAD;
        } else if (rGooglePhotos.isChecked()) {
            mode = MainActivity.MODE_GOOGLE_PHOTOS;
        } else if (rPhotoHost.isChecked()) {
            mode = MainActivity.MODE_PHOTO_HOST;
        } else {
            mode = MainActivity.MODE_STREAM;
        }
        String url = urlField.getText().toString().trim();
        String albumUrl = albumUrlField.getText().toString().trim();
        String photoHostUrl = normalizePhotoHostUrl();
        if (mode == MainActivity.MODE_PHOTO_HOST && TextUtils.isEmpty(albumUrl)) {
            Toast.makeText(this, "Enter a shared Google Photos or Drive link.", Toast.LENGTH_LONG).show();
            return;
        }
        if ((mode == MainActivity.MODE_GOOGLE_PHOTOS || mode == MainActivity.MODE_PHOTO_HOST)
                && !TextUtils.isEmpty(albumUrl)
                && !isValidWebUrl(albumUrl)) {
            Toast.makeText(this, "Album link must start with http:// or https://", Toast.LENGTH_LONG).show();
            return;
        }
        if (mode == MainActivity.MODE_PHOTO_HOST && !isValidWebUrl(photoHostUrl)) {
            Toast.makeText(this, "Photo Host URL must start with http:// or https://", Toast.LENGTH_LONG).show();
            return;
        }
        if (mode != MainActivity.MODE_BUNDLED && TextUtils.isEmpty(url)) {
            if (mode == MainActivity.MODE_GOOGLE_PHOTOS || mode == MainActivity.MODE_PHOTO_HOST) {
                url = "";
            } else {
                Toast.makeText(this, "Enter a video URL, or pick the built-in video.", Toast.LENGTH_LONG).show();
                return;
            }
        }
        if (mode != MainActivity.MODE_BUNDLED
                && mode != MainActivity.MODE_GOOGLE_PHOTOS
                && mode != MainActivity.MODE_PHOTO_HOST
                && !isValidWebUrl(url)) {
            Toast.makeText(this, "URL must start with http:// or https://", Toast.LENGTH_LONG).show();
            return;
        }
        String assistantUrl = normalizeAssistantUrl();
        if (!isValidAssistantUrl(assistantUrl)) {
            Toast.makeText(this, "Assistant URL must start with http:// or https://", Toast.LENGTH_LONG).show();
            return;
        }
        String hostedTilesUrl = normalizeHostedTilesUrl();
        if (!isValidWebUrl(hostedTilesUrl)) {
            Toast.makeText(this, "Hosted tiles URL must start with http:// or https://", Toast.LENGTH_LONG).show();
            return;
        }
        int clockSize = normalizeClockSize();
        if (clockSize < 0) return;
        String clockColor = selectedClockColor();
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                .putString(MainActivity.KEY_URL, url)
                .putString(MainActivity.KEY_ALBUM_URL, albumUrl)
                .putString(MainActivity.KEY_PHOTO_HOST_URL, photoHostUrl)
                .putInt(MainActivity.KEY_MODE, mode)
                .putString(MainActivity.KEY_ASSISTANT_URL, assistantUrl)
                .putString(MainActivity.KEY_HOSTED_TILES_URL, hostedTilesUrl)
                .putString(MainActivity.KEY_TILE_RENDERER, selectedTileRenderer())
                .putString(MainActivity.KEY_CLOCK_COLOR, clockColor)
                .putInt(MainActivity.KEY_CLOCK_TEXT_SIZE_SP, clockSize)
                .putBoolean(MainActivity.KEY_TILE_CLOCK_ENABLED, tileClock == null || tileClock.isChecked())
                .putBoolean(MainActivity.KEY_TILE_WEATHER_ENABLED, tileWeather != null && tileWeather.isChecked())
                .putBoolean(MainActivity.KEY_TILE_STOCKS_ENABLED, tileStocks != null && tileStocks.isChecked())
                .putBoolean(MainActivity.KEY_TILE_GREETING_ENABLED, tileGreeting != null && tileGreeting.isChecked())
                .putBoolean(MainActivity.KEY_TILE_BIRTHDAYS_ENABLED, tileBirthdays != null && tileBirthdays.isChecked())
                .apply();
        setResult(RESULT_OK);
        finish();
    }

    private String selectedClockColor() {
        if (rClockWhite != null && rClockWhite.isChecked()) return "#FFFFFF";
        if (rClockAmber != null && rClockAmber.isChecked()) return "#FFB000";
        if (rClockCyan != null && rClockCyan.isChecked()) return "#00E5FF";
        return MainActivity.DEFAULT_CLOCK_COLOR;
    }

    private String selectedTileRenderer() {
        if (rTileHosted != null && rTileHosted.isChecked()) return MainActivity.TILE_RENDERER_HOSTED;
        return MainActivity.TILE_RENDERER_NATIVE;
    }

    private String normalizeHostedTilesUrl() {
        if (hostedTilesUrlField == null) return MainActivity.DEFAULT_HOSTED_TILES_URL;
        String value = hostedTilesUrlField.getText().toString().trim();
        return TextUtils.isEmpty(value) ? MainActivity.DEFAULT_HOSTED_TILES_URL : value;
    }

    private int normalizeClockSize() {
        if (clockSizeField == null) return MainActivity.DEFAULT_CLOCK_TEXT_SIZE_SP;
        String value = clockSizeField.getText().toString().trim();
        if (TextUtils.isEmpty(value)) return MainActivity.DEFAULT_CLOCK_TEXT_SIZE_SP;
        try {
            int size = Integer.parseInt(value);
            if (size < MainActivity.MIN_CLOCK_TEXT_SIZE_SP || size > MainActivity.MAX_CLOCK_TEXT_SIZE_SP) {
                Toast.makeText(this, "Clock font size must be between 24 and 72.", Toast.LENGTH_LONG).show();
                return -1;
            }
            return size;
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Clock font size must be a number.", Toast.LENGTH_LONG).show();
            return -1;
        }
    }

    private boolean saveAssistantUrl() {
        String assistantUrl = normalizeAssistantUrl();
        if (!isValidAssistantUrl(assistantUrl)) {
            Toast.makeText(this, "Assistant URL must start with http:// or https://", Toast.LENGTH_LONG).show();
            return false;
        }
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                .putString(MainActivity.KEY_ASSISTANT_URL, assistantUrl)
                .apply();
        return true;
    }

    private String normalizeAssistantUrl() {
        if (assistantUrlField == null) return MainActivity.DEFAULT_ASSISTANT_URL;
        String assistantUrl = assistantUrlField.getText().toString().trim();
        if (TextUtils.isEmpty(assistantUrl)) return MainActivity.DEFAULT_ASSISTANT_URL;
        return assistantUrl;
    }

    private String normalizePhotoHostUrl() {
        if (photoHostUrlField == null) return MainActivity.DEFAULT_PHOTO_HOST_URL;
        String photoHostUrl = photoHostUrlField.getText().toString().trim();
        if (TextUtils.isEmpty(photoHostUrl)) return MainActivity.DEFAULT_PHOTO_HOST_URL;
        return photoHostUrl;
    }

    private boolean isValidAssistantUrl(String url) {
        return isValidWebUrl(url);
    }

    private boolean isValidWebUrl(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    private boolean hasBundledVideo() {
        try {
            for (String n : getAssets().list("")) if ("slideshow.mp4".equals(n)) return true;
        } catch (Exception ignored) { }
        return false;
    }

    private String lastRemoteRefreshText(SharedPreferences p) {
        long last = p.getLong(MainActivity.KEY_LAST_REMOTE_REFRESH_MS, 0);
        if (last <= 0) return "Remote settings have not synced yet. Local defaults are ready.";
        return "Last web settings sync: " + DateFormat.getDateTimeInstance().format(new Date(last));
    }

    private void loadPairingQr(final String pairingUrl) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    String qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data="
                            + URLEncoder.encode(pairingUrl, "UTF-8");
                    HttpURLConnection c = (HttpURLConnection) new URL(qrUrl).openConnection();
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(10000);
                    c.connect();
                    InputStream in = c.getInputStream();
                    final Bitmap bitmap = BitmapFactory.decodeStream(in);
                    in.close();
                    if (bitmap != null) {
                        ui.post(new Runnable() {
                            public void run() {
                                pairingQr.setImageBitmap(bitmap);
                            }
                        });
                    }
                } catch (Exception ignored) { }
            }
        }).start();
    }

    // ---- tiny view helpers ----
    private LinearLayout addExpandableSection(LinearLayout parent, final String title, String summary, boolean expanded) {
        final LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setVisibility(expanded ? View.VISIBLE : View.GONE);

        final Button header = new Button(this);
        header.setAllCaps(false);
        header.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        header.setTextSize(20f);
        header.setTextColor(Color.WHITE);
        header.setBackgroundColor(Color.parseColor("#202638"));
        header.setPadding(dp(14), 0, dp(14), 0);
        header.setMinHeight(dp(64));
        header.setText((expanded ? "v  " : ">  ") + title);
        header.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean show = content.getVisibility() != View.VISIBLE;
                content.setVisibility(show ? View.VISIBLE : View.GONE);
                header.setText((show ? "v  " : ">  ") + title);
            }
        });
        parent.addView(header, wide(dp(18)));

        if (!TextUtils.isEmpty(summary)) {
            TextView summaryView = help(summary);
            summaryView.setPadding(dp(10), dp(8), dp(10), dp(2));
            parent.addView(summaryView);
        }

        content.setPadding(dp(10), dp(8), dp(10), dp(8));
        parent.addView(content);
        return content;
    }

    private TextView title(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(Color.WHITE);
        v.setTextSize(26f);
        v.setPadding(0, 0, 0, dp(12));
        return v;
    }

    private TextView label(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(Color.parseColor("#B5BCCB"));
        v.setTextSize(16f);
        v.setPadding(0, 0, 0, dp(8));
        return v;
    }

    private TextView fieldLabel(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(Color.WHITE);
        v.setTextSize(18f);
        v.setPadding(0, dp(4), 0, dp(4));
        return v;
    }

    private TextView help(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(Color.parseColor("#A7AFBF"));
        v.setTextSize(14f);
        v.setPadding(0, 0, 0, dp(6));
        return v;
    }

    private EditText readOnlyValue(String t) {
        EditText v = new EditText(this);
        v.setText(t);
        v.setTextColor(Color.WHITE);
        v.setTextSize(16f);
        v.setSingleLine(false);
        v.setMinHeight(dp(58));
        v.setPadding(dp(12), 0, dp(12), 0);
        v.setFocusable(false);
        v.setInputType(InputType.TYPE_NULL);
        v.setBackgroundColor(Color.parseColor("#202638"));
        return v;
    }

    private TextView sectionTitle(String t) {
        TextView v = title(t);
        v.setTextSize(21f);
        v.setPadding(0, dp(18), 0, dp(6));
        return v;
    }

    private RadioButton radio(String t) {
        RadioButton r = new RadioButton(this);
        r.setText(t);
        r.setTextColor(Color.WHITE);
        r.setTextSize(18f);
        r.setMinHeight(dp(56));
        r.setPadding(dp(8), 0, 0, 0);
        return r;
    }

    private CheckBox checkbox(String t, boolean checked) {
        CheckBox c = new CheckBox(this);
        c.setText(t);
        c.setTextColor(Color.WHITE);
        c.setTextSize(18f);
        c.setMinHeight(dp(56));
        c.setPadding(dp(8), 0, 0, 0);
        c.setChecked(checked);
        return c;
    }

    private Button bigButton(String t, String color) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextSize(20f);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor(color));
        b.setMinHeight(dp(60));
        return b;
    }

    private View spacer(int height) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height));
        return v;
    }

    private LinearLayout.LayoutParams wide(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        return lp;
    }

    private LinearLayout.LayoutParams imageBox(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(260), dp(260));
        lp.topMargin = topMargin;
        lp.gravity = Gravity.LEFT;
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
