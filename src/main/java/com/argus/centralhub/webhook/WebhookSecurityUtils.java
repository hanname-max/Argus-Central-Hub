package com.argus.centralhub.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class WebhookSecurityUtils {

    private WebhookSecurityUtils() {
    }

    public static String calculateHmacSha256(String secret, String payload) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmac.init(secretKeySpec);
            byte[] hash = hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new WebhookSecurityException("Failed to calculate HMAC-SHA256 signature", e);
        }
    }

    public static boolean verifyHmacSha256(String secret, String payload, String signature) {
        String expectedSignature = calculateHmacSha256(secret, payload);
        return MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), 
                                       signature.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean verifyHmacSha256WithPrefix(String secret, String payload, String signatureHeader, String prefix) {
        if (signatureHeader == null || !signatureHeader.startsWith(prefix)) {
            return false;
        }
        String signature = signatureHeader.substring(prefix.length());
        return verifyHmacSha256(secret, payload, signature);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static class WebhookSecurityException extends RuntimeException {
        public WebhookSecurityException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
