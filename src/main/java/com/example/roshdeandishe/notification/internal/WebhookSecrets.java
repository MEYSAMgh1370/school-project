package com.example.roshdeandishe.notification.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * رازِ مسیر وب‌هوکِ هر ربات مدرسه: ۲۵۶ بیت تصادفی؛ فقط هشِ SHA-256 آن ذخیره می‌شود.
 */
final class WebhookSecrets {

    private static final SecureRandom RANDOM = new SecureRandom();

    private WebhookSecrets() {
    }

    /** رازِ تازه، Base64-URL بدون padding. */
    static String newSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 به‌صورت hex با حروف کوچک. */
    static String hash(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 در دسترس نیست", e);
        }
    }
}
