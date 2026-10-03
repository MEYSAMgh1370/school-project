package com.example.roshdeandishe.notification.internal;

import org.springframework.stereotype.Component;

/**
 * کانال بله: پیام را به chat لینک‌شده‌ی کاربر می‌فرستد. کاربری که حساب بله‌اش
 * را لینک نکرده در این کانال در دسترس نیست و {@code false} می‌گیرد.
 */
@Component
class BaleChannel implements NotificationChannel {

    private final BaleLinkRepository baleLinkRepository;
    private final BaleClient baleClient;

    BaleChannel(BaleLinkRepository baleLinkRepository, BaleClient baleClient) {
        this.baleLinkRepository = baleLinkRepository;
        this.baleClient = baleClient;
    }

    @Override
    public boolean send(Long userId, String text) {
        return baleLinkRepository.findByUserId(userId)
                .map(link -> baleClient.sendMessage(link.getChatId(), text))
                .orElse(false);
    }
}
