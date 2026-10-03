package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.notification.NotificationFacade;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
class NotificationFacadeImpl implements NotificationFacade {

    private final List<NotificationChannel> channels;
    private final BaleWebhookService webhookService;

    NotificationFacadeImpl(List<NotificationChannel> channels, BaleWebhookService webhookService) {
        this.channels = channels;
        this.webhookService = webhookService;
    }

    /**
     * کانال‌ها به ترتیبِ ثبت امتحان می‌شوند و با اولین کانالی که پیام را
     * رساند متوقف می‌شود. با یک کانال (بله) این دقیقاً رفتار قبلی است؛
     * سیاستِ چندکاناله (ارسال به همه یا فقط اولین موفق) تصمیمی است که با
     * اضافه‌شدنِ کانال دوم باید گرفته شود (OpenSpec: changes/
     * notification-channel-abstraction.md).
     */
    @Override
    public boolean sendMessageToUser(Long userId, String text) {
        for (NotificationChannel channel : channels) {
            if (channel.send(userId, text)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void processIncomingUpdate(String rawJson) {
        webhookService.processRawUpdate(rawJson);
    }
}
