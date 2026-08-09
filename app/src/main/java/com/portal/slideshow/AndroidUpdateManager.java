package com.portal.slideshow;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ClipData;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

final class AndroidUpdateManager {
    static final String UPDATE_URL = "https://uniquepeople-web.vercel.app/api/android-update";
    static final int REQUEST_UNKNOWN_APP_SOURCE = 1701;
    private static final String PACKAGE_NAME = "com.portal.slideshow";
    private static final long MAX_APK_BYTES = 250L * 1024L * 1024L;
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static boolean checkStarted;
    private static File pendingApk;

    private AndroidUpdateManager() { }

    static void checkOnLaunch(final Activity activity) {
        if (checkStarted || activity == null || activity.isFinishing()) return;
        checkStarted = true;
        new Thread(new Runnable() {
            public void run() {
                try {
                    final UpdateManifest manifest = fetchManifest();
                    if (!manifest.enabled || manifest.versionCode <= currentVersionCode(activity)) return;
                    activity.runOnUiThread(new Runnable() {
                        public void run() { showAvailable(activity, manifest); }
                    });
                } catch (Exception ignored) {
                    // Update service is optional. Slideshow startup must remain offline-safe.
                }
            }
        }, "android-update-check").start();
    }

    static boolean handleActivityResult(Activity activity, int requestCode) {
        if (requestCode != REQUEST_UNKNOWN_APP_SOURCE) return false;
        if (pendingApk == null || !pendingApk.isFile()) {
            Toast.makeText(activity, "The downloaded update is no longer available.", Toast.LENGTH_LONG).show();
            return true;
        }
        if (canRequestInstalls(activity)) launchInstaller(activity, pendingApk);
        else Toast.makeText(activity, "Install permission was not enabled.", Toast.LENGTH_LONG).show();
        return true;
    }

    private static UpdateManifest fetchManifest() throws Exception {
        HttpURLConnection connection = openHttps(UPDATE_URL);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/json");
        int status = connection.getResponseCode();
        if (status != 200) throw new Exception("Update metadata returned HTTP " + status);
        String body = readText(connection.getInputStream(), 64 * 1024);
        return UpdateManifest.parse(new JSONObject(body));
    }

    private static void showAvailable(final Activity activity, final UpdateManifest manifest) {
        String message = "UniquePeople " + manifest.versionName + " is available.";
        if (!TextUtils.isEmpty(manifest.releaseNotes)) message += "\n\n" + manifest.releaseNotes;
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Update available")
                .setMessage(message)
                .setPositiveButton("Download", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) { download(activity, manifest); }
                })
                .setNegativeButton(manifest.required ? "Later" : "Not now", null)
                .create();
        dialog.setCanceledOnTouchOutside(!manifest.required);
        dialog.show();
    }

    private static void download(final Activity activity, final UpdateManifest manifest) {
        Toast.makeText(activity, "Downloading UniquePeople " + manifest.versionName + "…", Toast.LENGTH_LONG).show();
        new Thread(new Runnable() {
            public void run() {
                File partial = null;
                try {
                    File directory = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (directory == null) directory = activity.getFilesDir();
                    File complete = new File(directory, "uniquepeople-update.apk");
                    partial = new File(directory, "uniquepeople-update.apk.part");
                    downloadApk(manifest.apkUrl, partial);
                    verifyApk(activity, partial, manifest);
                    if (complete.exists() && !complete.delete()) throw new Exception("Could not replace the previous update.");
                    if (!partial.renameTo(complete)) throw new Exception("Could not finalize the downloaded update.");
                    pendingApk = complete;
                    activity.runOnUiThread(new Runnable() {
                        public void run() { requestInstall(activity); }
                    });
                } catch (final Exception error) {
                    if (partial != null) partial.delete();
                    activity.runOnUiThread(new Runnable() {
                        public void run() {
                            new AlertDialog.Builder(activity)
                                    .setTitle("Update failed")
                                    .setMessage(error.getMessage() == null ? "The update could not be verified." : error.getMessage())
                                    .setPositiveButton("OK", null)
                                    .show();
                        }
                    });
                }
            }
        }, "android-update-download").start();
    }

    private static void requestInstall(final Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("Ready to install")
                .setMessage("The update passed package, version, checksum, and signing-certificate verification.")
                .setPositiveButton("Install", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (canRequestInstalls(activity)) launchInstaller(activity, pendingApk);
                        else requestUnknownSourcePermission(activity);
                    }
                })
                .setNegativeButton("Later", null)
                .show();
    }

    private static void requestUnknownSourcePermission(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + activity.getPackageName()));
        activity.startActivityForResult(intent, REQUEST_UNKNOWN_APP_SOURCE);
    }

    private static boolean canRequestInstalls(Activity activity) {
        return Build.VERSION.SDK_INT < 26 || activity.getPackageManager().canRequestPackageInstalls();
    }

    private static void launchInstaller(Activity activity, File apk) {
        if (apk == null || !apk.isFile()) return;
        Uri uri = Uri.parse("content://" + UpdateFileProvider.AUTHORITY + "/update.apk");
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.setClipData(ClipData.newRawUri("UniquePeople update", uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    private static void downloadApk(String source, File destination) throws Exception {
        HttpURLConnection connection = openHttps(source);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        int status = connection.getResponseCode();
        if (status != 200) throw new Exception("APK download returned HTTP " + status);
        long declaredSize = connection.getContentLengthLong();
        if (declaredSize > MAX_APK_BYTES) throw new Exception("The update file is unexpectedly large.");

        InputStream in = connection.getInputStream();
        FileOutputStream out = new FileOutputStream(destination, false);
        byte[] buffer = new byte[32 * 1024];
        long total = 0;
        try {
            int count;
            while ((count = in.read(buffer)) != -1) {
                total += count;
                if (total > MAX_APK_BYTES) throw new Exception("The update file is unexpectedly large.");
                out.write(buffer, 0, count);
            }
            out.getFD().sync();
        } finally {
            try { in.close(); } finally { out.close(); }
        }
        if (total == 0) throw new Exception("The downloaded update is empty.");
    }

    private static void verifyApk(Activity activity, File apk, UpdateManifest manifest) throws Exception {
        if (!sha256(apk).equals(manifest.sha256)) throw new Exception("The downloaded update checksum does not match.");
        PackageManager pm = activity.getPackageManager();
        PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
        if (archive == null) throw new Exception("Android could not read the downloaded APK.");
        if (!PACKAGE_NAME.equals(archive.packageName) || !manifest.packageName.equals(archive.packageName)) {
            throw new Exception("The downloaded APK has the wrong package name.");
        }
        long archiveVersion = Build.VERSION.SDK_INT >= 28 ? archive.getLongVersionCode() : archive.versionCode;
        if (archiveVersion != manifest.versionCode || archiveVersion <= currentVersionCode(activity)) {
            throw new Exception("The downloaded APK has an unexpected version.");
        }
        String archiveCertificate = certificateSha256(archive);
        PackageInfo installed = pm.getPackageInfo(PACKAGE_NAME, PackageManager.GET_SIGNING_CERTIFICATES);
        String installedCertificate = certificateSha256(installed);
        if (!archiveCertificate.equals(manifest.certificateSha256)
                || !archiveCertificate.equals(installedCertificate)) {
            throw new Exception("The downloaded APK signing certificate does not match.");
        }
    }

    private static long currentVersionCode(Activity activity) throws Exception {
        PackageInfo info = activity.getPackageManager().getPackageInfo(PACKAGE_NAME, 0);
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    private static String certificateSha256(PackageInfo info) throws Exception {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
            signatures = info.signingInfo.hasMultipleSigners()
                    ? info.signingInfo.getApkContentsSigners()
                    : info.signingInfo.getSigningCertificateHistory();
        } else {
            signatures = info.signatures;
        }
        if (signatures == null || signatures.length != 1) throw new Exception("Expected exactly one APK signer.");
        return hex(MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray()));
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        FileInputStream in = new FileInputStream(file);
        byte[] buffer = new byte[32 * 1024];
        try {
            int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
        } finally {
            in.close();
        }
        return hex(digest.digest());
    }

    private static String hex(byte[] bytes) {
        StringBuilder output = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) output.append(String.format(Locale.US, "%02x", value & 0xff));
        return output.toString();
    }

    private static HttpURLConnection openHttps(String value) throws Exception {
        URL url = new URL(value);
        if (!"https".equalsIgnoreCase(url.getProtocol()) || !TextUtils.isEmpty(url.getUserInfo())) {
            throw new Exception("Update URLs must use HTTPS without embedded credentials.");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        return connection;
    }

    private static String readText(InputStream in, int limit) throws Exception {
        byte[] buffer = new byte[8192];
        StringBuilder output = new StringBuilder();
        int total = 0;
        try {
            int count;
            while ((count = in.read(buffer)) != -1) {
                total += count;
                if (total > limit) throw new Exception("Update metadata is too large.");
                output.append(new String(buffer, 0, count, "UTF-8"));
            }
        } finally {
            in.close();
        }
        return output.toString();
    }

    static final class UpdateManifest {
        final boolean enabled;
        final String packageName;
        final long versionCode;
        final String versionName;
        final String apkUrl;
        final String sha256;
        final String certificateSha256;
        final boolean required;
        final String releaseNotes;

        private UpdateManifest(boolean enabled, String packageName, long versionCode, String versionName,
                               String apkUrl, String sha256, String certificateSha256,
                               boolean required, String releaseNotes) {
            this.enabled = enabled;
            this.packageName = packageName;
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apkUrl = apkUrl;
            this.sha256 = sha256;
            this.certificateSha256 = certificateSha256;
            this.required = required;
            this.releaseNotes = releaseNotes;
        }

        static UpdateManifest parse(JSONObject json) throws Exception {
            if (json.optInt("schemaVersion", -1) != 1) throw new Exception("Unsupported update manifest schema.");
            boolean enabled = json.optBoolean("enabled", false);
            String packageName = json.optString("packageName", "");
            if (!PACKAGE_NAME.equals(packageName)) throw new Exception("Update manifest package does not match.");
            if (!enabled) return new UpdateManifest(false, packageName, 0, "", "", "", "", false, "");

            long versionCode = json.optLong("versionCode", 0);
            String versionName = json.optString("versionName", "").trim();
            String apkUrl = json.optString("apkUrl", "").trim();
            String sha256 = normalizeDigest(json.optString("sha256", ""));
            String certificateSha256 = normalizeDigest(json.optString("certificateSha256", ""));
            if (versionCode < 1 || TextUtils.isEmpty(versionName)) throw new Exception("Update version metadata is invalid.");
            URL parsedUrl = new URL(apkUrl);
            if (!"https".equalsIgnoreCase(parsedUrl.getProtocol()) || !TextUtils.isEmpty(parsedUrl.getUserInfo())) {
                throw new Exception("Update APK URL must use HTTPS.");
            }
            return new UpdateManifest(true, packageName, versionCode, versionName, parsedUrl.toString(),
                    sha256, certificateSha256, json.optBoolean("required", false),
                    json.optString("releaseNotes", "").trim());
        }

        private static String normalizeDigest(String value) throws Exception {
            String digest = value == null ? "" : value.trim().toLowerCase(Locale.US);
            if (!digest.matches("[a-f0-9]{64}")) throw new Exception("Update digest is invalid.");
            return digest;
        }
    }
}
