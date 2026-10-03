package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.notification.NotificationFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * پیام را به همه‌ی کانال‌های موجود می‌فرستد. شکست (false) یا Exception در یک
 * کانال نباید مانع کانال‌های بعدی شود - پورت best-effort است، پس Exception
 * اینجا مهار و لاگ می‌شود، نه بالا داده.
 */
@Service
class NotificationFacadeImpl implements NotificationFacade {

    private static final Logger log = LoggerFactory.getLogger(NotificationFacadeImpl.class);

    private final List<NotificationChannel> channels;

    NotificationFacadeImpl(List<NotificationChannel> channels) {
        this.channels = channels;
    }

    @Override
    public boolean sendToUser(Long userId, String text) {
        boolean delivered = false;
        for (NotificationChannel channel : channels) {
            try {
                delivered |= channel.sendToUser(userId, text);
            } catch (RuntimeException e) {
                log.warn("کانال اطلاع‌رسانی {} برای userId={} با خطا مواجه شد؛ کانال‌های بعدی ادامه می‌یابند.",
                        channel.getClass().getSimpleName(), userId, e);
            }
        }
        return delivered;
    }
}
