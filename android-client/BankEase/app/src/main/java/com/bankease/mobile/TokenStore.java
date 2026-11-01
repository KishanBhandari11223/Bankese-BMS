package com.bankease.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class TokenStore {

    private static final String KEY_ALIAS = "bankease_access_token_key";
    private static final String PREFS = "secure_session";
    private static final String IV = "token_iv";
    private static final String CIPHERTEXT = "token_ciphertext";
    private static final String ROLE = "session_role";

    private final SharedPreferences preferences;

    public TokenStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized void saveToken(String token) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] ciphertext = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            boolean saved = preferences.edit()
                    .putString(IV, Base64.encodeToString(
                            cipher.getIV(), Base64.NO_WRAP))
                    .putString(CIPHERTEXT, Base64.encodeToString(
                            ciphertext, Base64.NO_WRAP))
                    .commit();
            if (!saved) {
                throw new IllegalStateException("Unable to save session");
            }
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to protect session", ex);
        }
    }

    public synchronized String getToken() {
        String encodedIv = preferences.getString(IV, null);
        String encodedCiphertext = preferences.getString(CIPHERTEXT, null);
        if (encodedIv == null || encodedCiphertext == null) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(
                    128, Base64.decode(encodedIv, Base64.NO_WRAP)));
            byte[] plaintext = cipher.doFinal(
                    Base64.decode(encodedCiphertext, Base64.NO_WRAP));
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            clearToken();
            return null;
        }
    }

    public synchronized void clearToken() {
        preferences.edit().remove(IV).remove(CIPHERTEXT).remove(ROLE).commit();
    }

    public synchronized void saveRole(String role) {
        preferences.edit().putString(ROLE, role).commit();
    }

    public synchronized String getRole() {
        return preferences.getString(ROLE, "");
    }

    private SecretKey getOrCreateKey() throws GeneralSecurityException {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            java.security.Key existingKey = keyStore.getKey(KEY_ALIAS, null);
            if (existingKey instanceof SecretKey) {
                return (SecretKey) existingKey;
            }
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            keyGenerator.init(new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build());
            return keyGenerator.generateKey();
        } catch (java.io.IOException | java.security.cert.CertificateException ex) {
            throw new GeneralSecurityException(ex);
        }
    }
}