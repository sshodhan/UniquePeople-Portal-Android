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

public class SettingsActivity extends Activity {

    private static final int REQ_QR_SCAN = 42;

    private EditText urlField;
    private EditText albumUrlField;
    private EditText photoHostUrlField;
    private EditText assistantUrlField;
    private EditText pairingUrlField;
    private ImageView pairingQr;
    private RadioButton rPhotoHost, rGooglePhotos, rStream, rDownload, rBundled;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("UniquePeople Settings");

        SharedPreferences p = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String url = p.getString(MainActivity.KEY_URL, "");
        String albumUrl = MainActivity.getAlbumUrl(p);
        String photoHostUrl = p.getString(MainActivity.KEY_PHOTO_HOST_URL, MainActivity.DEFAULT_PHOTO_HOST_URL);
        String assistantUrl = p.getString(MainActivity.KEY_ASSISTANT_URL, MainActivity.DEFAULT_ASSISTANT_URL);
        int mode = p.getInt(MainActivity.KEY_MODE, MainActivity.MODE_GOOGLE_PHOTOS);
        boolean hasBundled = hasBundledVideo();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#10131A"));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(28);
        col.setPadding(pad, pad, pad, pad);
        scroll.addView(col);

        col.addView(title("UniquePeople Display Settings"));
        col.addView(label("Choose what UniquePeople shows. Leave the shared photo link blank to use the built-in default photos."));

        final String deviceId = MainActivity.getOrCreateDeviceId(this);
        final String pairingUrl = MainActivity.buildPairingUrl(this);
        col.addView(sectionTitle("Pair This Portal"));
        col.addView(fieldLabel("Portal device ID"));
        col.addView(readOnlyValue(deviceId), wide(dp(4)));
        col.addView(help("This ID is unique to this Portal. Scan the QR code with your phone to manage only this device."));

        pairingQr = new ImageView(this);
        pairingQr.setBackgroundColor(Color.WHITE);
        pairingQr.setPadding(dp(10), dp(10), dp(10), dp(10));
        pairingQr.setAdjustViewBounds(true);
        col.addView(pairingQr, imageBox(dp(8)));
        loadPairingQr(pairingUrl);

        col.addView(fieldLabel("Phone setup link"));
        pairingUrlField = readOnlyValue(pairingUrl);
        col.addView(pairingUrlField, wide(dp(4)));

        Button copyPairing = bigButton("Copy phone setup link", "#00796B");
        copyPairing.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("UniquePeople setup link", pairingUrl));
                Toast.makeText(SettingsActivity.this, "Pairing link copied.", Toast.LENGTH_SHORT).show();
            }
        });
        col.addView(copyPairing, wide(dp(8)));

        Button openPairing = bigButton("Open setup page on this Portal", "#33394A");
        openPairing.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(pairingUrl)));
            }
        });
        col.addView(openPairing, wide(dp(8)));

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
        col.addView(refreshRemote, wide(dp(12)));

        col.addView(sectionTitle("Photo Source"));
        col.addView(fieldLabel("Shared Google Photos or Drive link"));
        col.addView(help("Optional. Paste a public/shared album or folder link. This is the main way each user customizes the app without changing the APK."));
        albumUrlField = new EditText(this);
        albumUrlField.setHint("https://photos.app.goo.gl/... or https://drive.google.com/...");
        albumUrlField.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        albumUrlField.setText(albumUrl);
        albumUrlField.setTextColor(Color.WHITE);
        albumUrlField.setHintTextColor(Color.parseColor("#7A8090"));
        albumUrlField.setTextSize(18f);
        albumUrlField.setMinHeight(dp(64));
        col.addView(albumUrlField, wide(dp(4)));

        Button scanQr = bigButton("Scan QR code with Portal camera", "#00796B");
        scanQr.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivityForResult(new Intent(SettingsActivity.this, QrScanActivity.class), REQ_QR_SCAN);
            }
        });
        col.addView(scanQr, wide(dp(10)));

        col.addView(sectionTitle("Display Mode"));
        col.addView(help("Direct shared-link mode is best for most people. Use Photo Host only if you have a separate website that renders the album."));
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
        col.addView(group, wide(dp(8)));

        col.addView(sectionTitle("Advanced Website Viewer"));
        col.addView(fieldLabel("Photo Host viewer URL"));
        col.addView(help("Optional. Only used when Photo Host mode is selected. The app opens this URL with albumUrl=<your shared link>."));
        photoHostUrlField = new EditText(this);
        photoHostUrlField.setHint(MainActivity.DEFAULT_PHOTO_HOST_URL);
        photoHostUrlField.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        photoHostUrlField.setText(photoHostUrl);
        photoHostUrlField.setTextColor(Color.WHITE);
        photoHostUrlField.setHintTextColor(Color.parseColor("#7A8090"));
        photoHostUrlField.setTextSize(18f);
        photoHostUrlField.setMinHeight(dp(64));
        col.addView(photoHostUrlField, wide(dp(4)));

        col.addView(sectionTitle("Video Fallback"));
        col.addView(fieldLabel("Video URL"));
        col.addView(help("Optional. Used only for Stream or Download video modes. Leave blank for photo modes and default photos."));
        urlField = new EditText(this);
        urlField.setHint("https://example.com/family.mp4");
        urlField.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        urlField.setText(url);
        urlField.setTextColor(Color.WHITE);
        urlField.setHintTextColor(Color.parseColor("#7A8090"));
        urlField.setTextSize(18f);
        urlField.setMinHeight(dp(64));
        col.addView(urlField, wide(dp(4)));

        if (mode == MainActivity.MODE_GOOGLE_PHOTOS) rGooglePhotos.setChecked(true);
        else if (mode == MainActivity.MODE_PHOTO_HOST) rPhotoHost.setChecked(true);
        else if (mode == MainActivity.MODE_DOWNLOAD) rDownload.setChecked(true);
        else if (mode == MainActivity.MODE_BUNDLED && hasBundled) rBundled.setChecked(true);
        else rStream.setChecked(true);

        col.addView(sectionTitle("Portal Assistant"));
        col.addView(fieldLabel("Assistant web app URL"));
        col.addView(help("Optional. This should point to the browser assistant app. API keys stay on that server, not inside this Android APK."));

        assistantUrlField = new EditText(this);
        assistantUrlField.setHint(MainActivity.DEFAULT_ASSISTANT_URL);
        assistantUrlField.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        assistantUrlField.setText(assistantUrl);
        assistantUrlField.setTextColor(Color.WHITE);
        assistantUrlField.setHintTextColor(Color.parseColor("#7A8090"));
        assistantUrlField.setTextSize(18f);
        assistantUrlField.setMinHeight(dp(64));
        col.addView(assistantUrlField, wide(dp(4)));

        Button openAssistant = bigButton("Open Assistant", "#00796B");
        openAssistant.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (saveAssistantUrl()) {
                    startActivity(new android.content.Intent(SettingsActivity.this, AssistantActivity.class));
                }
            }
        });
        col.addView(openAssistant, wide(dp(16)));

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
        int mode = p.getInt(MainActivity.KEY_MODE, MainActivity.MODE_GOOGLE_PHOTOS);
        if (mode == MainActivity.MODE_PHOTO_HOST) rPhotoHost.setChecked(true);
        else rGooglePhotos.setChecked(true);
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
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                .putString(MainActivity.KEY_URL, url)
                .putString(MainActivity.KEY_ALBUM_URL, albumUrl)
                .putString(MainActivity.KEY_PHOTO_HOST_URL, photoHostUrl)
                .putInt(MainActivity.KEY_MODE, mode)
                .putString(MainActivity.KEY_ASSISTANT_URL, assistantUrl)
                .apply();
        setResult(RESULT_OK);
        finish();
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
