package com.portal.slideshow;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class DeviceEnrollment {
    static final class HttpStatusException extends Exception {
        final int statusCode;
        HttpStatusException(String method, int statusCode) {
            super(method + " request failed (HTTP " + statusCode + ").");
            this.statusCode = statusCode;
        }
    }
    interface Callback {
        void onComplete(String memoryKey, Exception error);
    }

    interface SettingCallback {
        void onComplete(boolean enabled, Exception error);
    }

    private static final String KEY_ALIAS = "uniquepeople_portal_attested_identity_v2";
    private static final String STORAGE_KEY_ALIAS = "uniquepeople_portal_credential_storage";
    private static final String MEMORY_CIPHER_PREF = "assistant_memory_credential";
    private static final String MEMORY_IV_PREF = "assistant_memory_credential_iv";
    private static final String PENDING_CHALLENGE_PREF = "assistant_enrollment_challenge_v2";
    private static final String PENDING_CHALLENGE_TOKEN_PREF = "assistant_enrollment_challenge_token_v2";
    private static final String DEFAULT_ENROLLMENT_URL =
            "https://uniquepeople-web.vercel.app/api/device-enroll";

    static String savedMemoryKey(Context context) {
        try {
            android.content.SharedPreferences preferences =
                    context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
            String encodedCiphertext = preferences.getString(MEMORY_CIPHER_PREF, "");
            String encodedIv = preferences.getString(MEMORY_IV_PREF, "");
            if (encodedCiphertext.isEmpty() || encodedIv.isEmpty()) return "";
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, credentialStorageKey(),
                    new GCMParameterSpec(128, Base64.decode(encodedIv, Base64.NO_WRAP)));
            return new String(cipher.doFinal(
                    Base64.decode(encodedCiphertext, Base64.NO_WRAP)), StandardCharsets.UTF_8).trim();
        } catch (Exception error) {
            PortalLogger.error(context, MainActivity.DEFAULT_ASSISTANT_URL,
                    MainActivity.getOrCreateDeviceId(context), "credential_decrypt_failed",
                    "credential_read", 0, error);
            return "";
        }
    }

    static void ensureEnrolled(final Context context, final String deviceId,
                               final String assistantUrl, final Callback callback) {
        final String existing = savedMemoryKey(context);
        if (!existing.isEmpty()) {
            PortalLogger.event(context, assistantUrl, deviceId, "credential_reused",
                    "credential_read", 0);
            callback.onComplete(existing, null);
            return;
        }

        new Thread(new Runnable() {
            public void run() {
                long startedAt = System.currentTimeMillis();
                String stage = "keystore";
                PortalLogger.event(context, assistantUrl, deviceId, "enrollment_started",
                        "begin", 0);
                try {
                    String endpoint = enrollmentUrl(assistantUrl);
                    String challenge = pendingChallenge(context);
                    String challengeToken = pendingChallengeToken(context);
                    if (challenge.isEmpty() || challengeToken.isEmpty() || !hasIdentityKey()) {
                        clearPendingEnrollment(context);
                        stage = "begin";
                        JSONObject begin = request(endpoint, "POST", new JSONObject()
                                .put("action", "attest-begin")
                                .put("deviceId", deviceId));
                        challenge = begin.optString("challenge", "");
                        challengeToken = begin.optString("challengeToken", "");
                        if (challenge.isEmpty() || challengeToken.isEmpty()) {
                            throw new IllegalStateException("Enrollment returned no challenge.");
                        }
                        savePendingChallenge(context, challenge, challengeToken);
                        stage = "attest";
                        generateAttestedKeyPair(challenge);
                    }

                    stage = "complete";
                    Signature signer = Signature.getInstance("SHA256withECDSA");
                    signer.initSign(identityPrivateKey());
                    signer.update(challengeToken.getBytes(StandardCharsets.UTF_8));
                    JSONObject complete = request(endpoint, "POST", new JSONObject()
                            .put("action", "attest-complete")
                            .put("deviceId", deviceId)
                            .put("challenge", challenge)
                            .put("challengeToken", challengeToken)
                            .put("signature", Base64.encodeToString(signer.sign(), Base64.NO_WRAP))
                            .put("certificateChain", encodedCertificateChain()));

                    String memoryKey = complete.optString("memoryKey", "").trim();
                    if (memoryKey.isEmpty()) throw new IllegalStateException("Enrollment returned no memory key.");
                    stage = "credential_save";
                    saveMemoryKey(context, memoryKey);
                    clearPendingChallenge(context);
                    PortalLogger.event(context, assistantUrl, deviceId, "enrollment_succeeded",
                            "credential_saved", System.currentTimeMillis() - startedAt);
                    callback.onComplete(memoryKey, null);
                } catch (Exception error) {
                    if ("complete".equals(stage) && error instanceof HttpStatusException
                            && ((HttpStatusException) error).statusCode == 401) {
                        try {
                            clearPendingEnrollment(context);
                            PortalLogger.event(context, assistantUrl, deviceId,
                                    "enrollment_retry_reset", "attestation_rejected", 0);
                        } catch (Exception resetError) {
                            PortalLogger.error(context, assistantUrl, deviceId,
                                    "enrollment_retry_reset_failed", "keystore",
                                    System.currentTimeMillis() - startedAt, resetError);
                        }
                    }
                    PortalLogger.error(context, assistantUrl, deviceId, "enrollment_failed",
                            stage, System.currentTimeMillis() - startedAt, error);
                    callback.onComplete("", error);
                }
            }
        }).start();
    }

    static void setMarinEnabled(final Context context, final String deviceId,
                                final String assistantUrl, final boolean enabled,
                                final SettingCallback callback) {
        new Thread(new Runnable() {
            public void run() {
                long startedAt = System.currentTimeMillis();
                PortalLogger.event(context, assistantUrl, deviceId, "marin_toggle_requested",
                        enabled ? "enable" : "disable", 0);
                try {
                    String endpoint = deviceConfigUrl(assistantUrl);
                    JSONObject response = request(endpoint, "PATCH", new JSONObject()
                            .put("deviceId", deviceId)
                            .put("marinEnabled", enabled));
                    boolean saved = response.getJSONObject("config").optBoolean("marinEnabled", true);
                    PortalLogger.event(context, assistantUrl, deviceId, "marin_toggle_saved",
                            saved ? "enabled" : "disabled", System.currentTimeMillis() - startedAt);
                    callback.onComplete(saved, null);
                } catch (Exception error) {
                    PortalLogger.error(context, assistantUrl, deviceId, "marin_toggle_failed",
                            enabled ? "enable" : "disable", System.currentTimeMillis() - startedAt, error);
                    callback.onComplete(!enabled, error);
                }
            }
        }).start();
    }

    private static String enrollmentUrl(String assistantUrl) {
        try {
            URL parsed = new URL(assistantUrl);
            return new URL(parsed.getProtocol(), parsed.getHost(), parsed.getPort(), "/api/device-enroll").toString();
        } catch (Exception ignored) {
            return DEFAULT_ENROLLMENT_URL;
        }
    }

    private static String deviceConfigUrl(String assistantUrl) {
        try {
            URL parsed = new URL(assistantUrl);
            return new URL(parsed.getProtocol(), parsed.getHost(), parsed.getPort(), "/api/device-config").toString();
        } catch (Exception ignored) {
            return "https://uniquepeople-web.vercel.app/api/device-config";
        }
    }

    private static void generateAttestedKeyPair(String challenge) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS);
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
        generator.initialize(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAttestationChallenge(Base64.decode(challenge, Base64.DEFAULT))
                .build());
        generator.generateKeyPair();
    }

    private static boolean hasIdentityKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        return store.containsAlias(KEY_ALIAS) && store.getCertificateChain(KEY_ALIAS) != null;
    }

    private static JSONArray encodedCertificateChain() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        Certificate[] chain = store.getCertificateChain(KEY_ALIAS);
        if (chain == null || chain.length == 0) {
            throw new IllegalStateException("Android Keystore returned no attestation certificate chain.");
        }
        JSONArray encoded = new JSONArray();
        for (Certificate certificate : chain) {
            encoded.put(Base64.encodeToString(certificate.getEncoded(), Base64.NO_WRAP));
        }
        return encoded;
    }

    private static PrivateKey identityPrivateKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        return (PrivateKey) store.getKey(KEY_ALIAS, null);
    }

    private static String pendingChallenge(Context context) {
        return context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .getString(PENDING_CHALLENGE_PREF, "").trim();
    }

    private static String pendingChallengeToken(Context context) {
        return context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .getString(PENDING_CHALLENGE_TOKEN_PREF, "").trim();
    }

    private static void savePendingChallenge(Context context, String challenge, String challengeToken) {
        context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).edit()
                .putString(PENDING_CHALLENGE_PREF, challenge)
                .putString(PENDING_CHALLENGE_TOKEN_PREF, challengeToken).commit();
    }

    private static void clearPendingChallenge(Context context) {
        context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).edit()
                .remove(PENDING_CHALLENGE_PREF)
                .remove(PENDING_CHALLENGE_TOKEN_PREF).apply();
    }

    private static void clearPendingEnrollment(Context context) throws Exception {
        clearPendingChallenge(context);
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS);
    }

    private static void saveMemoryKey(Context context, String memoryKey) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, credentialStorageKey());
        byte[] encrypted = cipher.doFinal(memoryKey.getBytes(StandardCharsets.UTF_8));
        context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).edit()
                .putString(MEMORY_CIPHER_PREF, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(MEMORY_IV_PREF, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .apply();
    }

    private static SecretKey credentialStorageKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(STORAGE_KEY_ALIAS)) {
            return (SecretKey) store.getKey(STORAGE_KEY_ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(STORAGE_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }

    private static JSONObject request(String endpoint, String method, JSONObject payload) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);
        boolean patchOverride = "PATCH".equals(method);
        connection.setRequestMethod(patchOverride ? "POST" : method);
        if (patchOverride) connection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = connection.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                code < 400 ? connection.getInputStream() : connection.getErrorStream(),
                StandardCharsets.UTF_8));
        StringBuilder response = new StringBuilder();
        for (String line; (line = reader.readLine()) != null;) response.append(line);
        if (code >= 400) throw new HttpStatusException(method, code);
        return new JSONObject(response.toString());
    }
}
