package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.notification.NotificationFacade.DeliveryResult;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Optional;

/**
 * کانال بله: پیام را به chat لینک‌شده‌ی کاربر می‌فرستد. اگر مدرسه رباتِ ACTIVE داشته باشد فقط از لینک و توکنِ همان
 * ربات می‌رود (کاربرِ لینک‌نشده به آن ربات NOT_LINKED است و به ربات مشترک بازنمی‌گردد)؛ بدون مدرسه، بدون ربات یا با
 * رباتِ DISABLED از لینک مشترک. پاسخ 401 از ربات مدرسه یعنی توکن باطل شده و ربات DISABLED می‌شود. کاربری که حساب
 * بله‌اش را لینک نکرده در این کانال در دسترس نیست.
 */
@Component
class BaleChannel implements NotificationChannel {

    private final BaleLinkRepository baleLinkRepository;
    private final BaleClient baleClient;
    private final BotTokens botTokens;
    private final SchoolBotRepository schoolBots;
    private final Clock clock;

    @Autowired
    BaleChannel(BaleLinkRepository baleLinkRepository, BaleClient baleClient, BotTokens botTokens,
                SchoolBotRepository schoolBots) {
        this(baleLinkRepository, baleClient, botTokens, schoolBots, Clock.systemUTC());
    }

    BaleChannel(BaleLinkRepository baleLinkRepository, BaleClient baleClient, BotTokens botTokens,
                SchoolBotRepository schoolBots, Clock clock) {
        this.baleLinkRepository = baleLinkRepository;
        this.baleClient = baleClient;
        this.botTokens = botTokens;
        this.schoolBots = schoolBots;
        this.clock = clock;
    }

    @Override
    public boolean send(Long userId, String text) {
        return deliver(userId, text) == DeliveryResult.SENT;
    }

    @Override
    public DeliveryResult deliver(Long userId, String text) {
        return deliver(userId, null, text);
    }

    @Override
    public DeliveryResult deliver(Long userId, Long schoolId, String text) {
        Optional<SchoolBotEntity> bot = schoolId == null
                ? Optional.empty()
                : schoolBots.findBySchoolIdAndStatus(schoolId, SchoolBotStatus.ACTIVE);
        if (bot.isPresent()) {
            return deliverViaSchoolBot(bot.get(), userId, text);
        }
        return baleLinkRepository.findByUser(BotRef.SHARED, userId)
                .map(link -> baleClient.deliver(botTokens.tokenFor(BotRef.SHARED), link.getChatId(), text))
                .orElse(DeliveryResult.NOT_LINKED);
    }

    private DeliveryResult deliverViaSchoolBot(SchoolBotEntity bot, Long userId, String text) {
        BotRef ref = BotRef.school(bot.getId(), bot.getSchoolId());
        Optional<BaleLinkEntity> link = baleLinkRepository.findByUser(ref, userId);
        if (link.isEmpty()) {
            return DeliveryResult.NOT_LINKED;
        }
        BaleClient.SendResult sent = baleClient.deliverDetailed(botTokens.tokenFor(ref), link.get().getChatId(), text);
        if (sent.httpStatus() == HTTP_UNAUTHORIZED) {
            schoolBots.markStatus(bot.getId(), SchoolBotStatus.DISABLED, BotTexts.BOT_TOKEN_INVALID, clock.instant());
            return DeliveryResult.UNDELIVERABLE;
        }
        return sent.result();
    }

    private static final int HTTP_UNAUTHORIZED = 401;
}
