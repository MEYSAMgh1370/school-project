package com.example.roshdeandishe.notification.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * رمزنگاری توکنِ ربات‌های مدرسه با AES-256-GCM. خروجی: Base64(nonce ‖ ciphertext+tag) با nonce تصادفیِ ۹۶ بیتی.
 * کلید خالی یعنی «در دسترس نیست» (ثبت ربات رد می‌شود و بقیه‌ی برنامه عادی کار می‌کند)؛ کلیدِ غیرخالی با
 * طولِ غیر از ۳۲ بایت همان لحظه‌ی بالا آمدن خطا می‌دهد. توکن و کلید هرگز لاگ نمی‌شوند.
 */
@Component
class TokenCipher {

    /** نسخه‌ی کلیدِ فعلی؛ برای رمزنگاریِ دوباره با کلیدِ تازه در آینده. */
    static final int CURRENT_KEY_VERSION = 1;

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    record Encrypted(String ciphertext, int keyVersion) {
    }

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    TokenCipher(@Value("${roshdeandishe.bale.token-encryption-key}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            this.key = null;
            return;
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("BALE_TOKEN_ENCRYPTION_KEY باید Base64 معتبر باشد");
        }
        if (raw.length != 32) {
            throw new IllegalStateException("BALE_TOKEN_ENCRYPTION_KEY باید دقیقاً ۳۲ بایت (۲۵۶ بیت) باشد");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    boolean available() {
        return key != null;
    }

    Encrypted encrypt(String plain) {
        requireKey();
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[NONCE_BYTES + enc.length];
            System.arraycopy(nonce, 0, out, 0, NONCE_BYTES);
            System.arraycopy(enc, 0, out, NONCE_BYTES, enc.length);
            return new Encrypted(Base64.getEncoder().encodeToString(out), CURRENT_KEY_VERSION);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("رمزنگاری توکن ناموفق بود");
        }
    }

    String decrypt(String ciphertext, int keyVersion) {
        requireKey();
        if (keyVersion != CURRENT_KEY_VERSION) {
            throw new IllegalStateException("نسخه‌ی کلید توکن پشتیبانی نمی‌شود");
        }
        try {
            byte[] all = Base64.getDecoder().decode(ciphertext);
            if (all.length <= NONCE_BYTES) {
                throw new IllegalStateException("متن رمزشده‌ی توکن نامعتبر است");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(all, 0, NONCE_BYTES)));
            return new String(cipher.doFinal(all, NONCE_BYTES, all.length - NONCE_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // عمداً بدون علتِ اصلی: پیام نباید چیزی از متن/کلید لو بدهد.
            throw new IllegalStateException("رمزگشایی توکن ناموفق بود");
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException("کلید رمزنگاری توکن بله تنظیم نشده است");
        }
    }
}
