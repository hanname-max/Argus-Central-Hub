package com.argus.centralhub.feishu;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public class FeishuSecurityUtils {

    private FeishuSecurityUtils() {
    }

    public static String generateSign(String timestamp, String nonce, String encryptKey, String body) {
        try {
            String content = timestamp + nonce + encryptKey + body;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            throw new FeishuSecurityException("Failed to generate sign", e);
        }
    }

    public static boolean verifySignature(String timestamp, String nonce, String encryptKey,
                                           String body, String signature) {
        String expectedSign = generateSign(timestamp, nonce, encryptKey, body);
        return expectedSign.equals(signature);
    }

    public static String decrypt(String encryptKey, String encryptData) {
        try {
            byte[] key = sha256(encryptKey);
            byte[] iv = new byte[16];
            System.arraycopy(key, 0, iv, 0, 16);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            byte[] decoded = Base64.getDecoder().decode(encryptData);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new FeishuSecurityException("Failed to decrypt data", e);
        }
    }

    public static String encrypt(String encryptKey, String data) {
        try {
            byte[] key = sha256(encryptKey);
            byte[] iv = new byte[16];
            System.arraycopy(key, 0, iv, 0, 16);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

            byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new FeishuSecurityException("Failed to encrypt data", e);
        }
    }

    public static String calculateHmacSha256(String key, String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmac.init(secretKeySpec);
            byte[] hash = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new FeishuSecurityException("Failed to calculate HMAC-SHA256", e);
        }
    }

    private static byte[] sha256(String data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return md.digest(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static class FeishuSecurityException extends RuntimeException {
        public FeishuSecurityException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
