package com.portal.slideshow;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Figma Page 3 settings shell. V1 preferences remain the persistence contract. */
public class SettingsActivity extends Activity {
    private static final int REQ_QR_SCAN = 42;
    private static final int BG = Color.rgb(14, 17, 24);
    private static final int SIDE = Color.rgb(9, 11, 16);
    private static final int CARD = Color.rgb(18, 21, 30);
    private static final int BORDER = Color.rgb(34, 42, 60);
    private static final int TEXT = Color.rgb(245, 247, 255);
    private static final int MUTED = Color.rgb(168, 181, 204);
    private static final int TEAL = Color.rgb(31, 184, 173);
    private static final int GREEN = Color.rgb(77, 214, 143);

    private FrameLayout contentHost;
    private TextView setupNav, assistantNav, advancedNav, updatesNav, sideDevice, sideStatus;
    private TextView assistantStatus, assistantDetails;
    private TextView updateStatus, updateDetails;
    private Switch marinToggle;
    private Button checkUpdateButton, installUpdateButton;
    private RadioButton nativeMode, webMode;
    private EditText photoHostField, videoField, assistantField;
    private Switch photoHostToggle;
    private PortalSettings draft;
    private boolean advanced;
    private boolean assistantTab;
    private boolean updates;
    private float updatePullStartY;
    private AndroidUpdateManager.UpdateManifest availableUpdate;
    private boolean photoHostEnabled;
    private final Set<String> enabledTiles = new HashSet<String>();
    private final Map<String, TextView> chips = new HashMap<String, TextView>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        draft = readSettings();
        photoHostEnabled = isPhotoHostMode();
        setContentView(shell());
        showSetup();
    }

    private View shell() {
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(BG);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams bodyLp = new FrameLayout.LayoutParams(-1, -1); bodyLp.bottomMargin = dp(78);
        root.addView(body, bodyLp);
        body.addView(sidebar(), new LinearLayout.LayoutParams(dp(300), -1));
        contentHost = new FrameLayout(this); body.addView(contentHost, new LinearLayout.LayoutParams(0, -1, 1));
        FrameLayout.LayoutParams footerLp = new FrameLayout.LayoutParams(-1, dp(78)); footerLp.gravity = Gravity.BOTTOM;
        root.addView(footer(), footerLp);
        return root;
    }

    private View sidebar() {
        LinearLayout side = column(); side.setPadding(dp(34), dp(34), dp(24), dp(28)); side.setBackgroundColor(SIDE);
        side.addView(txt("UniquePeople", 25, TEXT, true));
        TextView sub = txt("V" + installedVersionName() + " Portal settings", 14, MUTED, false); side.addView(sub, top(5));
        setupNav = nav("Setup"); assistantNav = nav("Assistant"); advancedNav = nav("Advanced"); updatesNav = nav("Updates");
        side.addView(setupNav, top(54)); side.addView(assistantNav, full()); side.addView(advancedNav, full()); side.addView(updatesNav, full());
        setupNav.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showSetup(); }});
        assistantNav.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showAssistant(); }});
        advancedNav.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showAdvanced(); }});
        updatesNav.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showUpdates(); }});
        side.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
        LinearLayout device = column(); device.setPadding(dp(16), dp(16), dp(16), dp(16)); device.setBackground(box(CARD, BORDER, 12));
        sideDevice = txt(deviceName(), 15, TEXT, true); device.addView(sideDevice);
        sideStatus = txt("●  " + syncStatus(), 12, GREEN, false); device.addView(sideStatus, top(6));
        side.addView(device, full());
        return side;
    }

    private View footer() {
        LinearLayout footer = row(); footer.setGravity(Gravity.CENTER_VERTICAL); footer.setPadding(dp(30), dp(12), dp(30), dp(12)); footer.setBackgroundColor(SIDE);
        footer.addView(txt(buildLabel(), 13, GREEN, false), new LinearLayout.LayoutParams(0, -2, 1));
        Button refresh = button("Refresh from web", false), cancel = button("Cancel", false), save = button("Save & play", true);
        refresh.setOnClickListener(new View.OnClickListener() { public void onClick(final View v) { refresh(v); }});
        cancel.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { setResult(RESULT_CANCELED); finish(); }});
        save.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { save(); }});
        footer.addView(refresh, buttonParams()); footer.addView(cancel, buttonParams()); footer.addView(save, buttonParams());
        return footer;
    }

    private void showSetup() {
        captureCurrent(); advanced = false; assistantTab = false; updates = false; styleNav(); contentHost.removeAllViews();
        LinearLayout content = page();
        content.addView(hero()); content.addView(identityBar(), top(18));
        content.addView(txt("Scan Album", 20, TEXT, true), section()); content.addView(scanCard());
        content.addView(txt("Content Tiles", 20, TEXT, true), section()); content.addView(tilesCard());
        mount(content);
    }

    private View hero() {
        LinearLayout card = row(); card.setGravity(Gravity.CENTER_VERTICAL); card.setPadding(dp(34), dp(30), dp(34), dp(30)); card.setBackground(box(CARD, BORDER, 16));
        LinearLayout copy = column(); copy.addView(eyebrow("THIS SMART DISPLAY")); copy.addView(txt(deviceName(), 31, TEXT, true), top(8));
        TextView desc = txt("Scan this code to easily manage photos, linked accounts, and custom integrations from your phone or browser.", 16, MUTED, false);
        desc.setLineSpacing(0, 1.12f); copy.addView(desc, top(12)); copy.addView(txt("https://uniquepeople-web.vercel.app/settings", 15, TEAL, true), top(18));
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView qr = new ImageView(this); qr.setPadding(dp(10), dp(10), dp(10), dp(10)); qr.setBackgroundColor(Color.WHITE); qr.setImageBitmap(makeQr(MainActivity.buildPairingUrl(this), 420));
        card.addView(qr, new LinearLayout.LayoutParams(dp(220), dp(220)));
        return card;
    }

    private View identityBar() {
        LinearLayout bar = row(); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(dp(24), dp(20), dp(24), dp(20)); bar.setBackground(box(CARD, BORDER, 12));
        bar.addView(identity("DEVICE ID", draft.deviceId, TEXT), new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(divider(), new LinearLayout.LayoutParams(dp(1), dp(46)));
        bar.addView(identity("CURRENT ALBUM", currentAlbumDisplay(), TEXT), new LinearLayout.LayoutParams(0, -2, 1.35f));
        bar.addView(divider(), new LinearLayout.LayoutParams(dp(1), dp(46)));
        bar.addView(identity("STATUS", "●  Connected", GREEN), new LinearLayout.LayoutParams(0, -2, .7f));
        return bar;
    }

    private View scanCard() {
        LinearLayout card = row(); card.setGravity(Gravity.CENTER_VERTICAL); card.setPadding(dp(25), dp(22), dp(25), dp(22)); card.setBackground(box(CARD, BORDER, 12));
        LinearLayout copy = column(); copy.addView(txt("Use camera to scan album QR code", 18, TEXT, true));
        copy.addView(txt("Point this portal at a Google Photos shared album QR code to connect directly.", 14, MUTED, false), top(7));
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        Button open = button("Open Camera", true); open.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { startActivityForResult(new Intent(SettingsActivity.this, QrScanActivity.class), REQ_QR_SCAN); }});
        card.addView(open, new LinearLayout.LayoutParams(dp(180), dp(54)));
        return card;
    }

    private View tilesCard() {
        LinearLayout card = column(); card.setPadding(dp(25), dp(22), dp(25), dp(22)); card.setBackground(box(CARD, BORDER, 12));
        LinearLayout modeRow = row(); modeRow.setGravity(Gravity.CENTER_VERTICAL); modeRow.addView(txt("Rendering engine", 16, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        RadioGroup group = new RadioGroup(this); group.setOrientation(RadioGroup.HORIZONTAL); nativeMode = radio("Native"); webMode = radio("Web driven"); group.addView(nativeMode); group.addView(webMode);
        if (draft.tileMode == PortalSettings.TileMode.WEB_DRIVEN) webMode.setChecked(true); else nativeMode.setChecked(true); modeRow.addView(group); card.addView(modeRow);
        enabledTiles.clear(); enabledTiles.addAll(draft.enabledTiles); chips.clear();
        LinearLayout chipRow = row(); addChip(chipRow, "clock", "Clock"); addChip(chipRow, "daily_greeting", "Daily greeting"); addChip(chipRow, "weather", "Weather"); addChip(chipRow, "markets", "Markets"); addChip(chipRow, "birthdays", "Birthdays");
        HorizontalScrollView scroll = new HorizontalScrollView(this); scroll.setHorizontalScrollBarEnabled(false); scroll.addView(chipRow); card.addView(scroll, top(18));
        card.addView(txt("Shown when no album or web content is available", 13, MUTED, false), top(14));
        return card;
    }

    private void showAdvanced() {
        captureCurrent(); advanced = true; assistantTab = false; updates = false; styleNav(); contentHost.removeAllViews();
        LinearLayout content = page(); content.addView(txt("Advanced Settings", 31, TEXT, true));
        content.addView(txt("Manual URLs, fallback options, and assistant configuration.", 16, MUTED, false), top(8));
        LinearLayout cards = row(); cards.setGravity(Gravity.TOP);
        LinearLayout left = advancedCard("PHOTOS & VIDEO SLIDESHOW", "Photo Host & Fallbacks");
        LinearLayout hostMode = row(); hostMode.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout hostModeCopy = column();
        hostModeCopy.addView(fieldLabel("Photo Host viewer mode"));
        hostModeCopy.addView(txt("Off uses Google Photos/Drive directly. On tries the Photo Host viewer.", 13, MUTED, false), top(5));
        hostMode.addView(hostModeCopy, new LinearLayout.LayoutParams(0, -2, 1));
        photoHostToggle = new Switch(this);
        photoHostToggle.setContentDescription("Photo Host viewer mode");
        photoHostToggle.setChecked(photoHostEnabled);
        photoHostToggle.setShowText(false);
        hostMode.addView(photoHostToggle, new LinearLayout.LayoutParams(dp(72), dp(52)));
        left.addView(hostMode, top(22));
        photoHostField = input("Photo Host URL", "https://photos.example.com/slideshow", draft.photoHostUrl);
        videoField = input("Video Fallback URL", "https://example.com/fallback.mp4", draft.videoFallbackUrl);
        left.addView(fieldLabel("Photo Host URL"), top(18)); left.addView(photoHostField, inputParams());
        left.addView(fieldLabel("Video Fallback URL"), top(18)); left.addView(videoField, inputParams());
        left.addView(txt("Used when the primary content source is unavailable", 13, MUTED, false), top(14));
        LinearLayout right = advancedCard("PORTAL ASSISTANT", "Assistant Integration");
        assistantField = input("Assistant URL", "https://example.com/assistant", draft.assistantUrl);
        right.addView(fieldLabel("Assistant URL"), top(22)); right.addView(assistantField, inputParams());
        right.addView(txt("●  " + (draft.assistantConnected ? "Assistant connected" : "Assistant not connected"), 14, draft.assistantConnected ? GREEN : MUTED, false), top(18));
        Button open = button("Open Assistant", true); open.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openAssistant(); }}); LinearLayout.LayoutParams openLp = full(); openLp.height = dp(56); openLp.topMargin = dp(24); right.addView(open, openLp);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, -2, 1); leftLp.rightMargin = dp(10); LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, -2, 1); rightLp.leftMargin = dp(10);
        cards.addView(left, leftLp); cards.addView(right, rightLp); content.addView(cards, top(34)); mount(content);
    }

    private void showAssistant() {
        captureCurrent(); advanced = false; assistantTab = true; updates = false; styleNav(); contentHost.removeAllViews();
        LinearLayout content = page();
        content.addView(txt("Assistant", 31, TEXT, true));
        content.addView(txt("Marin is enrolled automatically for this Portal.", 16, MUTED, false), top(8));

        final boolean enabled = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .getBoolean(MainActivity.KEY_MARIN_ENABLED, true);
        boolean enrolled = !DeviceEnrollment.savedMemoryKey(this).isEmpty();
        LinearLayout connection = advancedCard("MARIN", "Assistant connection");
        LinearLayout toggleRow = row(); toggleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout toggleCopy = column(); toggleCopy.addView(txt("Use Marin on this Portal", 16, TEXT, true));
        toggleCopy.addView(txt("Controls the Marin persona and conversation memory.", 13, MUTED, false), top(5));
        toggleRow.addView(toggleCopy, new LinearLayout.LayoutParams(0, -2, 1));
        marinToggle = new Switch(this); marinToggle.setContentDescription("Use Marin on this Portal");
        marinToggle.setChecked(enabled); marinToggle.setShowText(false);
        toggleRow.addView(marinToggle, new LinearLayout.LayoutParams(dp(72), dp(52)));
        connection.addView(toggleRow, top(18));
        marinToggle.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { updateMarinEnabled(marinToggle.isChecked(), enabled); }
        });

        assistantStatus = txt(!enabled ? "●  Marin is off" : enrolled ? "●  Memory connected" : "●  Finishing setup", 18,
                !enabled ? MUTED : enrolled ? GREEN : Color.rgb(255, 190, 100), true);
        connection.addView(assistantStatus, top(20));
        assistantDetails = txt(!enabled
                ? "Turn Marin on to use the assistant persona and household memory on this Portal. Existing notes are preserved."
                : enrolled
                ? "This Portal is securely enrolled. Marin can save and use household notes between sessions."
                : "Setup begins when you open Marin. You can also retry it now.",
                14, MUTED, false);
        assistantDetails.setLineSpacing(0, 1.12f); connection.addView(assistantDetails, top(10));
        connection.addView(identity("PORTAL ID", draft.deviceId, TEXT), top(22));

        LinearLayout actions = row();
        if (enabled) {
            Button open = button("Open Marin", true);
            open.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openAssistant(); }});
            actions.addView(open, new LinearLayout.LayoutParams(dp(190), dp(56)));
        }
        if (enabled && !enrolled) {
            Button retry = button("Retry setup", false);
            retry.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { retryAssistantEnrollment(v); }});
            LinearLayout.LayoutParams retryLp = new LinearLayout.LayoutParams(dp(190), dp(56)); retryLp.leftMargin = dp(12); actions.addView(retry, retryLp);
        }
        connection.addView(actions, top(24));
        content.addView(connection, top(30));

        LinearLayout privacy = advancedCard("PRIVACY", "Private to this Portal");
        privacy.addView(txt("The device identity and stored assistant credential are protected by Android Keystore. The credential is never shown in Settings or written to diagnostic logs.", 14, MUTED, false), top(18));
        content.addView(privacy, top(18));
        mount(content);
    }

    private void updateMarinEnabled(final boolean requested, final boolean previous) {
        marinToggle.setEnabled(false);
        assistantStatus.setText(requested ? "●  Turning Marin on…" : "●  Turning Marin off…");
        assistantDetails.setText("Saving this Portal's assistant setting.");
        DeviceEnrollment.setMarinEnabled(this, draft.deviceId, draft.assistantUrl, requested,
                new DeviceEnrollment.SettingCallback() {
            public void onComplete(final boolean saved, final Exception error) {
                runOnUiThread(new Runnable() { public void run() {
                    if (error != null) {
                        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                                .putBoolean(MainActivity.KEY_MARIN_ENABLED, previous).apply();
                        marinToggle.setChecked(previous); marinToggle.setEnabled(true);
                        assistantStatus.setText("●  Setting could not be saved");
                        assistantStatus.setTextColor(Color.rgb(255, 145, 125));
                        assistantDetails.setText("Check this Portal's internet connection and try again.");
                        return;
                    }
                    getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                            .putBoolean(MainActivity.KEY_MARIN_ENABLED, saved).apply();
                    showAssistant();
                }});
            }
        });
    }

    private void retryAssistantEnrollment(final View button) {
        button.setEnabled(false);
        assistantStatus.setText("●  Setting up Marin…"); assistantStatus.setTextColor(Color.rgb(255, 190, 100));
        assistantDetails.setText("Securely connecting this Portal to Marin's memory.");
        DeviceEnrollment.ensureEnrolled(this, draft.deviceId, draft.assistantUrl, new DeviceEnrollment.Callback() {
            public void onComplete(String memoryKey, final Exception error) {
                runOnUiThread(new Runnable() { public void run() {
                    button.setEnabled(true);
                    if (error == null) showAssistant();
                    else {
                        assistantStatus.setText("●  Setup needs attention"); assistantStatus.setTextColor(Color.rgb(255, 145, 125));
                        if (error instanceof DeviceEnrollment.HttpStatusException
                                && ((DeviceEnrollment.HttpStatusException) error).statusCode == 409) {
                            assistantDetails.setText("This Portal ID belongs to a previous installation. An administrator must reset its enrollment before memory can reconnect.");
                        } else {
                            assistantDetails.setText("Marin memory could not connect. Check this Portal's internet connection and try again. Marin can still open without memory.");
                        }
                    }
                }});
            }
        });
    }

    private void showUpdates() {
        captureCurrent(); advanced = false; assistantTab = false; updates = true; styleNav(); contentHost.removeAllViews();
        LinearLayout content = page();
        content.addView(txt("Updates", 31, TEXT, true));
        content.addView(txt("View this Portal's installed build and check the hosted release channel.", 16, MUTED, false), top(8));

        LinearLayout build = advancedCard("INSTALLED BUILD", "UniquePeople " + installedVersionName());
        build.addView(txt("Version code " + installedVersionCode(), 16, TEXT, true), top(18));
        build.addView(txt("Package com.portal.slideshow", 14, MUTED, false), top(7));
        build.addView(txt("Release verification remains enforced for every downloaded APK.", 14, GREEN, false), top(14));
        content.addView(build, top(30));

        LinearLayout status = advancedCard("UPDATE CHANNEL", "Hosted updates");
        updateStatus = txt("Ready to check", 18, TEXT, true); status.addView(updateStatus, top(18));
        updateDetails = txt(lastUpdateCheckText(), 14, MUTED, false); updateDetails.setLineSpacing(0, 1.12f); status.addView(updateDetails, top(8));
        checkUpdateButton = button("Check for updates", true);
        checkUpdateButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { checkForUpdates(); }});
        LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(dp(220), dp(56)); checkLp.topMargin = dp(22); status.addView(checkUpdateButton, checkLp);
        installUpdateButton = button("Download and install", true); installUpdateButton.setVisibility(View.GONE);
        installUpdateButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (availableUpdate != null) AndroidUpdateManager.showAvailable(SettingsActivity.this, availableUpdate); }});
        LinearLayout.LayoutParams installLp = new LinearLayout.LayoutParams(dp(240), dp(56)); installLp.topMargin = dp(12); status.addView(installUpdateButton, installLp);
        status.addView(txt("Pull down from the top of this page to check again.", 13, MUTED, false), top(18));
        content.addView(status, top(18));
        mount(content);
    }

    private void checkForUpdates() {
        if (checkUpdateButton == null || !checkUpdateButton.isEnabled()) return;
        checkUpdateButton.setEnabled(false); installUpdateButton.setVisibility(View.GONE); availableUpdate = null;
        updateStatus.setText("Checking…"); updateStatus.setTextColor(TEXT); updateDetails.setText("Contacting the hosted update service.");
        AndroidUpdateManager.checkNow(this, new AndroidUpdateManager.CheckCallback() {
            public void onComplete(AndroidUpdateManager.UpdateManifest manifest, boolean available, String error) {
                checkUpdateButton.setEnabled(true);
                if (error != null) {
                    updateStatus.setText("Check failed"); updateStatus.setTextColor(Color.rgb(255, 145, 125)); updateDetails.setText(error + "\nPull down or tap Check for updates to retry."); return;
                }
                getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit().putLong("android_update_last_check_ms", System.currentTimeMillis()).apply();
                if (available) {
                    availableUpdate = manifest; updateStatus.setText("Update available"); updateStatus.setTextColor(GREEN);
                    updateDetails.setText("UniquePeople " + manifest.versionName + " (version code " + manifest.versionCode + ")\n" + (empty(manifest.releaseNotes) ? "A newer verified build is ready." : manifest.releaseNotes));
                    installUpdateButton.setVisibility(View.VISIBLE);
                } else {
                    updateStatus.setText("UniquePeople is up to date"); updateStatus.setTextColor(GREEN);
                    updateDetails.setText("Installed build " + installedVersionName() + " (version code " + installedVersionCode() + ") is current.");
                }
            }
        });
    }

    private void mount(LinearLayout content) {
        FrameLayout page = new FrameLayout(this); page.setBackgroundColor(BG);
        final ScrollView scroll = new ScrollView(this); scroll.addView(content); page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        if (updates) scroll.setOnTouchListener(new View.OnTouchListener() { public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) updatePullStartY = event.getY();
            if (event.getAction() == MotionEvent.ACTION_UP && scroll.getScrollY() == 0 && event.getY() - updatePullStartY > dp(80)) checkForUpdates();
            return false;
        }});
        TextView close = txt("×  Close Settings", 14, MUTED, true); close.setGravity(Gravity.CENTER); close.setBackground(box(BG, BORDER, 8)); close.setClickable(true); close.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { setResult(RESULT_CANCELED); finish(); }});
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(dp(170), dp(48)); closeLp.gravity = Gravity.TOP | Gravity.RIGHT; closeLp.topMargin = dp(28); closeLp.rightMargin = dp(36); page.addView(close, closeLp);
        contentHost.addView(page, new FrameLayout.LayoutParams(-1, -1));
    }

    private LinearLayout page() { LinearLayout content = column(); content.setPadding(dp(54), dp(92), dp(54), dp(44)); return content; }
    private LinearLayout advancedCard(String category, String title) { LinearLayout card = column(); card.setPadding(dp(28), dp(28), dp(28), dp(28)); card.setBackground(box(CARD, BORDER, 14)); card.addView(eyebrow(category)); card.addView(txt(title, 23, TEXT, true), top(8)); return card; }

    private PortalSettings readSettings() {
        SharedPreferences p = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE); Set<String> tiles = new HashSet<String>();
        boolean legacyProfile = !p.contains(MainActivity.KEY_TILE_RENDERER)
                && (p.contains(MainActivity.KEY_ALBUM_URL) || p.contains(MainActivity.KEY_MODE)
                || p.contains(MainActivity.KEY_CLOCK_COLOR));
        if (p.getBoolean(MainActivity.KEY_TILE_CLOCK_ENABLED, true)) tiles.add("clock");
        if (p.getBoolean(MainActivity.KEY_TILE_GREETING_ENABLED, !legacyProfile)) tiles.add("daily_greeting");
        if (p.getBoolean(MainActivity.KEY_TILE_WEATHER_ENABLED, !legacyProfile)) tiles.add("weather");
        if (p.getBoolean(MainActivity.KEY_TILE_STOCKS_ENABLED, false)) tiles.add("markets");
        if (p.getBoolean(MainActivity.KEY_TILE_BIRTHDAYS_ENABLED, false)) tiles.add("birthdays");
        String assistant = p.getString(MainActivity.KEY_ASSISTANT_URL, MainActivity.DEFAULT_ASSISTANT_URL);
        String renderer = p.getString(MainActivity.KEY_TILE_RENDERER,
                legacyProfile ? MainActivity.TILE_RENDERER_NATIVE : MainActivity.TILE_RENDERER_HOSTED);
        return new PortalSettings(MainActivity.getOrCreateDeviceId(this), p.getString(MainActivity.KEY_DEVICE_FRIENDLY_NAME, ""), MainActivity.getAlbumUrl(p), p.getString(MainActivity.KEY_CURRENT_ALBUM_NAME, ""), MainActivity.TILE_RENDERER_HOSTED.equals(renderer) ? PortalSettings.TileMode.WEB_DRIVEN : PortalSettings.TileMode.NATIVE, tiles, p.getString(MainActivity.KEY_PHOTO_HOST_URL, MainActivity.DEFAULT_PHOTO_HOST_URL), p.getString(MainActivity.KEY_URL, ""), assistant, validUrl(assistant));
    }

    private PortalSettings copy(String album, PortalSettings.TileMode mode, Set<String> tiles, String host, String video, String assistant) {
        return new PortalSettings(draft.deviceId, draft.deviceName, album, draft.currentAlbumName, mode, tiles, host, video, assistant, validUrl(assistant));
    }

    private void captureSetup() { if (advanced || nativeMode == null) return; draft = copy(draft.sharedAlbumUrl, webMode.isChecked() ? PortalSettings.TileMode.WEB_DRIVEN : PortalSettings.TileMode.NATIVE, enabledTiles, draft.photoHostUrl, draft.videoFallbackUrl, draft.assistantUrl); }
    private void captureAdvanced() { if (!advanced || photoHostField == null) return; photoHostEnabled = photoHostToggle != null && photoHostToggle.isChecked(); draft = copy(draft.sharedAlbumUrl, draft.tileMode, draft.enabledTiles, value(photoHostField), value(videoField), value(assistantField)); }
    private void captureCurrent() { if (advanced) captureAdvanced(); else if (!updates && !assistantTab) captureSetup(); }

    private void save() {
        captureCurrent();
        if (!optionalUrl(draft.photoHostUrl, "Photo Host URL") || !optionalUrl(draft.videoFallbackUrl, "Video Fallback URL") || !optionalUrl(draft.assistantUrl, "Assistant URL")) return;
        SharedPreferences.Editor editor = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                .putString(MainActivity.KEY_ALBUM_URL, safe(draft.sharedAlbumUrl))
                .putString(MainActivity.KEY_PHOTO_HOST_URL, orDefault(draft.photoHostUrl, MainActivity.DEFAULT_PHOTO_HOST_URL))
                .putString(MainActivity.KEY_URL, safe(draft.videoFallbackUrl))
                .putString(MainActivity.KEY_ASSISTANT_URL, orDefault(draft.assistantUrl, MainActivity.DEFAULT_ASSISTANT_URL))
                .putString(MainActivity.KEY_TILE_RENDERER, draft.tileMode == PortalSettings.TileMode.WEB_DRIVEN ? MainActivity.TILE_RENDERER_HOSTED : MainActivity.TILE_RENDERER_NATIVE)
                .putBoolean(MainActivity.KEY_TILE_CLOCK_ENABLED, draft.enabledTiles.contains("clock"))
                .putBoolean(MainActivity.KEY_TILE_GREETING_ENABLED, draft.enabledTiles.contains("daily_greeting"))
                .putBoolean(MainActivity.KEY_TILE_WEATHER_ENABLED, draft.enabledTiles.contains("weather"))
                .putBoolean(MainActivity.KEY_TILE_STOCKS_ENABLED, draft.enabledTiles.contains("markets"))
                .putBoolean(MainActivity.KEY_TILE_BIRTHDAYS_ENABLED, draft.enabledTiles.contains("birthdays"))
                .putInt(MainActivity.KEY_MODE, photoHostEnabled ? MainActivity.MODE_PHOTO_HOST : MainActivity.MODE_GOOGLE_PHOTOS);
        editor.apply();
        setResult(RESULT_OK); finish();
    }

    private void refresh(final View button) {
        button.setEnabled(false); MainActivity.refreshRemoteConfigAsync(this, new MainActivity.RemoteConfigCallback() { public void onComplete(boolean ok, String message) {
            button.setEnabled(true); if (ok) { draft = readSettings(); photoHostEnabled = isPhotoHostMode(); sideDevice.setText(deviceName()); sideStatus.setText("●  Synced just now"); if (advanced) showAdvanced(); else if (assistantTab) showAssistant(); else if (updates) showUpdates(); else showSetup(); } Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_LONG).show();
        }});
    }

    private void openAssistant() { captureAdvanced(); if (!optionalUrl(draft.assistantUrl, "Assistant URL")) return; Intent i = new Intent(this, AssistantActivity.class); i.putExtra("assistant_url", orDefault(draft.assistantUrl, MainActivity.DEFAULT_ASSISTANT_URL)); startActivity(i); }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (AndroidUpdateManager.handleActivityResult(this, requestCode)) return;
        if (requestCode == REQ_QR_SCAN && resultCode == RESULT_OK) {
            String album = data == null ? null : data.getStringExtra("album_url"); if (empty(album)) album = MainActivity.getAlbumUrl(getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE));
            draft = copy(album, draft.tileMode, draft.enabledTiles, draft.photoHostUrl, draft.videoFallbackUrl, draft.assistantUrl); Toast.makeText(this, "Album ready. Save & play to connect it.", Toast.LENGTH_LONG).show(); showSetup();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        AndroidUpdateManager.resumePendingInstall(this);
    }

    private void addChip(LinearLayout row, final String key, String label) { final TextView chip = txt(label, 14, TEXT, true); chip.setGravity(Gravity.CENTER); chip.setPadding(dp(18), 0, dp(18), 0); chip.setClickable(true); chips.put(key, chip); styleChip(chip, enabledTiles.contains(key)); chip.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (enabledTiles.contains(key)) enabledTiles.remove(key); else enabledTiles.add(key); styleChip(chip, enabledTiles.contains(key)); }}); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(48)); lp.rightMargin = dp(10); row.addView(chip, lp); }
    private void styleChip(TextView chip, boolean enabled) { chip.setTextColor(enabled ? TEXT : MUTED); chip.setBackground(box(enabled ? Color.rgb(20, 76, 75) : BG, enabled ? TEAL : BORDER, 24)); }
    private void styleNav() {
        boolean setup = !advanced && !assistantTab && !updates;
        setupNav.setTextColor(setup ? TEXT : MUTED); assistantNav.setTextColor(assistantTab ? TEXT : MUTED); advancedNav.setTextColor(advanced ? TEXT : MUTED); updatesNav.setTextColor(updates ? TEXT : MUTED);
        setupNav.setBackground(box(setup ? Color.rgb(18, 55, 57) : SIDE, setup ? TEAL : SIDE, 8));
        assistantNav.setBackground(box(assistantTab ? Color.rgb(18, 55, 57) : SIDE, assistantTab ? TEAL : SIDE, 8));
        advancedNav.setBackground(box(advanced ? Color.rgb(18, 55, 57) : SIDE, advanced ? TEAL : SIDE, 8));
        updatesNav.setBackground(box(updates ? Color.rgb(18, 55, 57) : SIDE, updates ? TEAL : SIDE, 8));
    }

    private Bitmap makeQr(String value, int size) { try { BitMatrix bits = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size); Bitmap image = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565); for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) image.setPixel(x, y, bits.get(x, y) ? Color.BLACK : Color.WHITE); return image; } catch (Exception e) { return null; } }
    private LinearLayout identity(String label, String value, int color) { LinearLayout item = column(); item.setPadding(dp(18), 0, dp(18), 0); item.addView(eyebrow(label)); item.addView(txt(value, 15, color, true), top(6)); return item; }
    private TextView eyebrow(String value) { TextView v = txt(value, 12, TEAL, true); v.setLetterSpacing(.12f); return v; }
    private TextView nav(String value) { TextView v = txt(value, 17, MUTED, true); v.setGravity(Gravity.CENTER_VERTICAL); v.setPadding(dp(18), 0, dp(18), 0); v.setMinHeight(dp(58)); v.setClickable(true); return v; }
    private Button button(String value, boolean primary) { Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setTextColor(primary ? Color.rgb(5, 26, 27) : TEXT); b.setBackground(box(primary ? TEAL : SIDE, primary ? TEAL : BORDER, 8)); return b; }
    private RadioButton radio(String value) { RadioButton r = new RadioButton(this); r.setText(value); r.setTextColor(TEXT); r.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{TEAL, MUTED})); return r; }
    private EditText input(String label, String hint, String value) { EditText e = new EditText(this); e.setText(value); e.setHint(hint); e.setContentDescription(label); e.setTextColor(TEXT); e.setHintTextColor(Color.rgb(105, 117, 138)); e.setTextSize(15); e.setSingleLine(); e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI); e.setPadding(dp(16), 0, dp(16), 0); e.setBackground(box(BG, BORDER, 8)); return e; }
    private TextView fieldLabel(String value) { return txt(value, 14, TEXT, true); }
    private TextView txt(String value, float size, int color, boolean bold) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); return v; }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private View divider() { View v = new View(this); v.setBackgroundColor(BORDER); return v; }
    private GradientDrawable box(int fill, int stroke, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); d.setStroke(dp(1), stroke); return d; }
    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams top(int margin) { LinearLayout.LayoutParams lp = full(); lp.topMargin = dp(margin); return lp; }
    private LinearLayout.LayoutParams section() { LinearLayout.LayoutParams lp = top(28); lp.bottomMargin = dp(12); return lp; }
    private LinearLayout.LayoutParams inputParams() { LinearLayout.LayoutParams lp = full(); lp.height = dp(62); lp.topMargin = dp(8); return lp; }
    private LinearLayout.LayoutParams buttonParams() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(178), dp(52)); lp.leftMargin = dp(12); return lp; }
    private boolean optionalUrl(String value, String label) { if (empty(value) || validUrl(value)) return true; Toast.makeText(this, label + " must start with http:// or https://", Toast.LENGTH_LONG).show(); return false; }
    private boolean validUrl(String value) { return !empty(value) && (value.startsWith("http://") || value.startsWith("https://")); }
    private String value(EditText field) { return field.getText().toString().trim(); }
    private String safe(String value) { return value == null ? "" : value; }
    private String orDefault(String value, String fallback) { return empty(value) ? fallback : value; }
    private boolean empty(String value) { return TextUtils.isEmpty(value); }
    private String deviceName() { return empty(draft.deviceName) ? "Family-Room-Portal" : draft.deviceName; }
    private String currentAlbumDisplay() { if (!empty(draft.currentAlbumName)) return draft.currentAlbumName; if (!empty(draft.sharedAlbumUrl)) return draft.sharedAlbumUrl; return "No album selected"; }
    private String syncStatus() { return getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getLong(MainActivity.KEY_LAST_REMOTE_REFRESH_MS, 0) > 0 ? "Synced just now" : "Connected"; }
    private String installedVersionName() { try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { return "Unknown"; } }
    private long installedVersionCode() { try { PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0); return android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode; } catch (Exception ignored) { return 0; } }
    private String buildLabel() { return "UniquePeople " + installedVersionName() + "  •  Build " + installedVersionCode(); }
    private String lastUpdateCheckText() { long value = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getLong("android_update_last_check_ms", 0); return value == 0 ? "No manual update check has run yet." : "Last checked " + android.text.format.DateUtils.getRelativeTimeSpanString(value); }
    private boolean isPhotoHostMode() { return getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getInt(MainActivity.KEY_MODE, MainActivity.MODE_GOOGLE_PHOTOS) == MainActivity.MODE_PHOTO_HOST; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
