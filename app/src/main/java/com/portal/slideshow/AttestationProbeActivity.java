package com.portal.slideshow;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.view.Gravity;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.MessageDigest;

/**
 * Isolated AUR-53 feasibility probe. This creates and immediately deletes a
 * dedicated attestation key; it never reads or changes Marin enrollment keys.
 */
public final class AttestationProbeActivity extends Activity {
    private static final String ALIAS = "uniquepeople_attestation_probe";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final TextView result = new TextView(this);
        result.setText("Running Android Key Attestation probe…");
        result.setTextColor(Color.WHITE);
        result.setTextSize(22f);
        result.setGravity(Gravity.CENTER);
        result.setPadding(48, 48, 48, 48);
        result.setBackgroundColor(Color.rgb(12, 18, 22));
        setContentView(result);

        new Thread(new Runnable() {
            public void run() {
                try {
                    final JSONObject evidence = runProbe();
                    final File output = new File(getExternalFilesDir(null), "attestation-probe.json");
                    try (FileOutputStream stream = new FileOutputStream(output)) {
                        stream.write(evidence.toString(2).getBytes(StandardCharsets.UTF_8));
                    }
                    runOnUiThread(new Runnable() {
                        public void run() {
                            result.setText("Attestation probe completed\n\nCertificate chain: "
                                    + evidence.optInt("certificateChainLength")
                                    + "\nEvidence: " + output.getAbsolutePath());
                        }
                    });
                } catch (final Exception error) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            result.setText("Attestation probe failed\n\n"
                                    + error.getClass().getSimpleName() + ": " + error.getMessage());
                        }
                    });
                } finally {
                    deleteProbeKey();
                }
            }
        }).start();
    }

    private JSONObject runProbe() throws Exception {
        deleteProbeKey();
        byte[] challenge = new byte[32];
        new SecureRandom().nextBytes(challenge);

        KeyPairGenerator generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
        generator.initialize(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAttestationChallenge(challenge)
                .build());
        generator.generateKeyPair();

        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        Certificate[] chain = store.getCertificateChain(ALIAS);
        if (chain == null || chain.length == 0) {
            throw new IllegalStateException("Android Keystore returned no attestation certificate chain.");
        }

        JSONArray encodedChain = new JSONArray();
        JSONArray subjects = new JSONArray();
        for (Certificate certificate : chain) {
            X509Certificate x509 = (X509Certificate) certificate;
            encodedChain.put(Base64.encodeToString(x509.getEncoded(), Base64.NO_WRAP));
            subjects.put(x509.getSubjectX500Principal().getName());
        }

        JSONObject evidence = new JSONObject();
        evidence.put("schemaVersion", 1);
        evidence.put("packageName", getPackageName());
        evidence.put("portalId", MainActivity.getOrCreateDeviceId(this));
        evidence.put("challenge", Base64.encodeToString(challenge, Base64.NO_WRAP));
        evidence.put("certificateChainLength", chain.length);
        evidence.put("certificateSubjects", subjects);
        evidence.put("rootSha256", hex(MessageDigest.getInstance("SHA-256")
                .digest(chain[chain.length - 1].getEncoded())));
        evidence.put("certificateChain", encodedChain);
        return evidence;
    }

    private static void deleteProbeKey() {
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS);
        } catch (Exception ignored) { }
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format("%02x", item & 0xff));
        return value.toString();
    }
}
