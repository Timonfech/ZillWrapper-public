package com.zillya.timonfech.zillwrapper.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

@Component
@Lazy
public class CryptoUtils {
    private final SecretKey aesKey;
    private final SecretKey hmacKey;
    private final boolean enabled;
    private final SecureRandom rnd = new SecureRandom();
    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_BITS = 128;

    public CryptoUtils(@Value("${APP_MASTER_KEY:}") String base64Master) throws Exception {
        if (base64Master == null || base64Master.isBlank()) {
            this.aesKey = null;
            this.hmacKey = null;
            this.enabled = false;
            return;
        }
        byte[] master = Base64.getDecoder().decode(base64Master);
        byte[][] keys = HkdfUtil.deriveAesAndHmac(master);
        this.aesKey = new SecretKeySpec(keys[0], "AES");
        this.hmacKey = new SecretKeySpec(keys[1], "HmacSHA256");
        this.enabled = true;
        // zero master and keys[] local copies where possible
        java.util.Arrays.fill(master, (byte)0);
        java.util.Arrays.fill(keys[0], (byte)0);
        java.util.Arrays.fill(keys[1], (byte)0);
    }

    @PostConstruct
    void registerHolder() {
        CryptoHolder.set(this);
    }

    // encrypt -> base64(iv||ciphertext||tag)
    public String encryptToBase64(String plain) throws Exception {
        requireEnabled();
        byte[] iv = new byte[GCM_IV_LEN];
        rnd.nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
        c.init(Cipher.ENCRYPT_MODE, aesKey, spec);
        byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        ByteBuffer bb = ByteBuffer.allocate(iv.length + ct.length);
        bb.put(iv);
        bb.put(ct);
        return Base64.getEncoder().encodeToString(bb.array());
    }

    // decrypt from base64(iv||ciphertext||tag)
    public String decryptFromBase64(String b64) throws Exception {
        requireEnabled();
        byte[] all = Base64.getDecoder().decode(b64);
        ByteBuffer bb = ByteBuffer.wrap(all);
        byte[] iv = new byte[GCM_IV_LEN];
        bb.get(iv);
        byte[] ct = new byte[bb.remaining()];
        bb.get(ct);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
        c.init(Cipher.DECRYPT_MODE, aesKey, spec);
        byte[] pt = c.doFinal(ct);
        String s = new String(pt, StandardCharsets.UTF_8);
        // try to clear pt
        java.util.Arrays.fill(pt, (byte)0);
        return s;
    }

    // HMAC-SHA256 -> base64
    public String hmacSha256Base64(String data) throws Exception {
        requireEnabled();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(hmacKey);
        byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        String b = Base64.getEncoder().encodeToString(out);
        java.util.Arrays.fill(out, (byte)0);
        return b;
    }
    private static final int ITERATIONS = 600000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LEN = 16;


    public String generateSalt() {
        byte[] salt = new byte[SALT_LEN];
        rnd.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public String hashPassword(String password, String saltB64) throws Exception {
        requireEnabled();
        byte[] salt = Base64.getDecoder().decode(saltB64);
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] hash = factory.generateSecret(spec).getEncoded();
        return Base64.getEncoder().encodeToString(hash);
    }

    public boolean verifyPassword(String password, String saltB64, String hashB64) throws Exception {
        String newHash = hashPassword(password, saltB64);
        return newHash.equals(hashB64);
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException("APP_MASTER_KEY is required when crypto features are used");
        }
    }
}
