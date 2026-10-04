package com.example.watchtest.uii;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Keeps the device token encrypted with a key that never leaves the Android Keystore. */
public final class KeystoreSecretStore implements SecretStore {
    private static final String ALIAS = "uii_device_token_key";
    private static final String PROVIDER = "AndroidKeyStore";
    private static final String TRANSFORM = "AES/GCM/NoPadding";

    private final SharedPreferences prefs;
    private volatile String cachedToken;

    public KeystoreSecretStore(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences("uii_secrets", Context.MODE_PRIVATE);
    }

    @Override public synchronized String token() {
        if (cachedToken != null) return cachedToken;
        String ct = prefs.getString("ct", null);
        String iv = prefs.getString("iv", null);
        if (ct == null || iv == null) return null;
        try {
            Cipher c = Cipher.getInstance(TRANSFORM);
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            cachedToken = new String(c.doFinal(Base64.decode(ct, Base64.NO_WRAP)), StandardCharsets.UTF_8);
            return cachedToken;
        } catch (Exception e) {
            // The key was lost (for example after a factory reset restore): the token is unusable, pair again.
            clear();
            return null;
        }
    }

    @Override public String deviceId() {
        return prefs.getString("device_id", null);
    }

    @Override public synchronized void save(String deviceId, String token) {
        try {
            Cipher c = Cipher.getInstance(TRANSFORM);
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] ct = c.doFinal(token.getBytes(StandardCharsets.UTF_8));
            // commit(), not apply(): the server issues this token once, so it must be on disk before we carry on.
            // With apply(), a crash right after pairing lost the token and left an orphan device on the server.
            boolean written = prefs.edit()
                    .putString("ct", Base64.encodeToString(ct, Base64.NO_WRAP))
                    .putString("iv", Base64.encodeToString(c.getIV(), Base64.NO_WRAP))
                    .putString("device_id", deviceId)
                    .commit();
            if (!written) throw new IllegalStateException("Could not persist the device token");
            cachedToken = token;
        } catch (Exception e) {
            throw new IllegalStateException("Could not store the device token", e);
        }
    }

    @Override public synchronized void clear() {
        cachedToken = null;
        prefs.edit().clear().commit();
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(PROVIDER);
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return (SecretKey) ks.getKey(ALIAS, null);
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER);
        g.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return g.generateKey();
    }
}
