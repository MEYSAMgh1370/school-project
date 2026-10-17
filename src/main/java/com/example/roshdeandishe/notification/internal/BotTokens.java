package com.example.roshdeandishe.notification.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * توکنِ یک ربات: ربات مشترک از پیکربندی، ربات مدرسه از ردیف {@code school_bots} (رمزگشایی‌شده). توکن هرگز لاگ
 * نمی‌شود. توکن گم‌شده رشته‌ی خالی است و {@link BaleClient} آن را «تماس نگیر» می‌فهمد.
 */
@Component
class BotTokens {

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
                .map(entity -> cipher.decrypt(entity.getTokenCiphertext(), entity.getTokenKeyVersion()))
                .orElse("");
    }
}
