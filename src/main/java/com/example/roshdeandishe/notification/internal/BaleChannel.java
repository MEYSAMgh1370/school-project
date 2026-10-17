package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.notification.NotificationFacade.DeliveryResult;

import org.springframework.stereotype.Component;

/**
 * کانال بله: پیام را به chat لینک‌شده‌ی کاربر می‌فرستد. کاربری که حساب بله‌اش
 * را لینک نکرده در این کانال در دسترس نیست و {@code false} می‌گیرد.
 */
@Component
class BaleChannel implements NotificationChannel {

    private final BaleLinkRepository baleLinkRepository;
    private final BaleClient baleClient;
    private final BotTokens botTokens;

    BaleChannel(BaleLinkRepository baleLinkRepository, BaleClient baleClient, BotTokens botTokens) {
        this.baleLinkRepository = baleLinkRepository;
        this.baleClient = baleClient;
        this.botTokens = botTokens;
    }

    @Override
    public boolean send(Long userId, String text) {
        return deliver(userId, text) == DeliveryResult.SENT;
    }

    @Override
    public DeliveryResult deliver(Long userId, String text) {
        return baleLinkRepository.findByUserId(userId)
                .map(link -> baleClient.deliver(botTokens.tokenFor(BotRef.SHARED), link.getChatId(), text))
                .orElse(DeliveryResult.NOT_LINKED);
    }
}
