package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.notification.NotificationFacade;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
class NotificationFacadeImpl implements NotificationFacade {

    private final List<NotificationChannel> channels;
    private final BaleWebhookService webhookService;
    private final BotResolver botResolver;

    NotificationFacadeImpl(List<NotificationChannel> channels, BaleWebhookService webhookService, BotResolver botResolver) {
        this.channels = channels;
        this.webhookService = webhookService;
        this.botResolver = botResolver;
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
        return sendMessageToUser(userId, null, text);
    }

    @Override
    public boolean sendMessageToUser(Long userId, Long schoolId, String text) {
        return deliverMessageToUser(userId, schoolId, text) == DeliveryResult.SENT;
    }

    /**
     * کانال‌ها به ترتیب امتحان می‌شوند و با اولین SENT متوقف می‌شود. وگرنه بهترین نتیجه‌ی غیرموفق برمی‌گردد:
     * اگر هر کانالی خطای موقت داده باشد TEMPORARY_FAILURE (تلاش دوباره ارزش دارد)، وگرنه UNDELIVERABLE اگر
     * کانالی قطعاً نرسانده، وگرنه NOT_LINKED (بدون هیچ کانال یا فقط کاربرِ لینک‌نشده).
     */
    @Override
    public DeliveryResult deliverMessageToUser(Long userId, String text) {
        return deliverMessageToUser(userId, null, text);
    }

    @Override
    public DeliveryResult deliverMessageToUser(Long userId, Long schoolId, String text) {
        DeliveryResult worst = DeliveryResult.NOT_LINKED;
        for (NotificationChannel channel : channels) {
            DeliveryResult result = channel.deliver(userId, schoolId, text);
            if (result == DeliveryResult.SENT) {
                return result;
            }
            if (result == DeliveryResult.TEMPORARY_FAILURE
                    || (result == DeliveryResult.UNDELIVERABLE && worst == DeliveryResult.NOT_LINKED)) {
                worst = result;
            }
        }
        return worst;
    }

    @Override
    public boolean processIncomingUpdate(String secret, String rawJson) {
        Optional<BotRef> bot = botResolver.resolve(secret);
        if (bot.isEmpty()) {
            return false;
        }
        webhookService.processRawUpdate(bot.get(), rawJson);
        return true;
    }
}
