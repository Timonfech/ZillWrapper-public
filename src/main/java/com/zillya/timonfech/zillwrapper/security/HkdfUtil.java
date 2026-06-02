package com.zillya.timonfech.zillwrapper.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

class HkdfUtil {
    // HKDF-Extract + Expand (HMAC-SHA256)
    public static byte[] extract(byte[] salt, byte[] ikm) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec key = new SecretKeySpec(salt == null ? new byte[32] : salt, "HmacSHA256");
        mac.init(key);
        return mac.doFinal(ikm);
    }

    public static byte[] expand(byte[] prk, byte[] info, int length) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec key = new SecretKeySpec(prk, "HmacSHA256");
        mac.init(key);
        byte[] result = new byte[length];
        byte[] t = new byte[0];
        int loc = 0;
        int counter = 1;
        while (loc < length) {
            mac.reset();
            mac.update(t);
            if (info != null) mac.update(info);
            mac.update((byte) counter);
            t = mac.doFinal();
            int toCopy = Math.min(t.length, length - loc);
            System.arraycopy(t, 0, result, loc, toCopy);
            loc += toCopy;
            counter++;
        }
        return result;
    }

    public static byte[][] deriveAesAndHmac(byte[] masterKey) throws Exception {
        // derive 64 bytes: first 32 aes, next 32 hmac
        byte[] prk = extract(null, masterKey);
        byte[] okm = expand(prk, "crypto-keys".getBytes(StandardCharsets.UTF_8), 64);
        byte[] aes = new byte[32];
        byte[] hmac = new byte[32];
        System.arraycopy(okm, 0, aes, 0, 32);
        System.arraycopy(okm, 32, hmac, 0, 32);
        return new byte[][]{aes, hmac};
    }
}