package com.example.roshdeandishe.notification.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * توکنِ یک ربات: ربات مشترک از پیکربندی، ربات مدرسه از ردیف {@code school_bots} (رمزگشایی‌شده). توکن هرگز لاگ
 * نمی‌شود. توکن گم‌شده رشته‌ی خالی است و {@link BaleClient} آن را «تماس نگیر» می‌فهمد.
 */
@Component
class BotTokens {

    private static final Logger log = LoggerFactory.getLogger(BotTokens.class);

    private final String sharedToken;
    private final SchoolBotRepository bots;
    private final TokenCipher cipher;

    BotTokens(
            @Value("${roshdeandishe.bale.bot-token}") String sharedToken,
            SchoolBotRepository bots,
            TokenCipher cipher
    ) {
        this.sharedToken = sharedToken == null ? "" : sharedToken;
        this.bots = bots;
        this.cipher = cipher;
    }

    String tokenFor(BotRef bot) {
        if (bot.isShared()) {
            return sharedToken;
        }
        return bots.findById(bot.botId())
                .map(entity -> decryptOrBlank(bot, entity))
                .orElse("");
    }

    /** شکستِ رمزگشایی (کلید یا ردیف خراب) فقط «تماس نگیر» می‌شود؛ نه توکن و نه متن رمزشده لاگ می‌شود. */
    private String decryptOrBlank(BotRef bot, SchoolBotEntity entity) {
        try {
            return cipher.decrypt(entity.getTokenCiphertext(), entity.getTokenKeyVersion());
        } catch (RuntimeException e) {
            log.warn("رمزگشایی توکن ربات {} شکست خورد: {}", bot.botId(), e.getClass().getSimpleName());
            return "";
        }
    }
}
