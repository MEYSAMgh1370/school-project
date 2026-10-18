package com.example.roshdeandishe.notification.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * تشخیص رباتِ یک وب‌هوک ورودی از روی بخش مخفی مسیر. راز مشترک (پیکربندی) با
 * مقایسه‌ی زمان‌ثابت تطبیق داده می‌شود و ربات {@link BotRef#SHARED} می‌دهد؛ وگرنه هشِ راز
 * میان رباتِ فعالِ مدارس جست‌وجو می‌شود. راز خالی هرگز با چیزی تطبیق نمی‌کند و راز هرگز لاگ نمی‌شود.
 */
@Component
class BotResolver {

    private final String sharedSecret;
    private final SchoolBotRepository bots;

    BotResolver(@Value("${roshdeandishe.bale.webhook-secret}") String sharedSecret, SchoolBotRepository bots) {
        this.sharedSecret = sharedSecret;
        this.bots = bots;
    }

    /** خالی یعنی راز ناشناخته است (کنترلر ۴۰۴ می‌دهد). */
    Optional<BotRef> resolve(String secret) {
        if (secret == null || secret.isBlank()) {
            return Optional.empty();
        }
        if (sharedSecret != null && !sharedSecret.isBlank() && constantTimeEquals(secret, sharedSecret)) {
            return Optional.of(BotRef.SHARED);
        }
        return bots.findByWebhookSecretHashAndStatus(WebhookSecrets.hash(secret), SchoolBotStatus.ACTIVE)
                .map(bot -> BotRef.school(bot.getId(), bot.getSchoolId()));
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
