package com.example.roshdeandishe.bale.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * پارس کردن بدنه‌ی خام وب‌هوک + صدا زدن BaleUpdateHandler. جدا از
 * BaleWebhookController نگه داشته شده تا BaleUpdate (یک نوع internal)
 * هرگز به بیرون از پکیج internal درز نکند - کنترلر (در ریشه‌ی ماژول، طبق
 * قرارداد پروژه) فقط رشته‌ی خام JSON را پاس می‌دهد.
 * <p>
 * عمداً یک {@code ObjectMapper} مستقل می‌سازد، نه یکی از Spring تزریق
 * می‌کند: چون spring-modulith-events-jackson هم Jackson 3 (tools.jackson)
 * را روی classpath می‌آورد، auto-configuration جکسون در این پروژه دیگر
 * یک Bean واحد و بدون‌ابهام از نوع {@code com.fasterxml.jackson.databind.ObjectMapper}
 * نمی‌سازد. برای پارس یک DTO ساده، customization سراسری جکسون لازم
 * نیست، پس این وابستگی مبهم را دور می‌زنیم.
 */
@Component
class BaleWebhookService {

    private static final Logger log = LoggerFactory.getLogger(BaleWebhookService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BaleUpdateHandler updateHandler;

    BaleWebhookService(BaleUpdateHandler updateHandler) {
        this.updateHandler = updateHandler;
    }

    /**
     * عمداً هر Exception را اینجا می‌بلعد: یک وب‌هوک باید همیشه سریع ۲۰۰
     * برگرداند - یک 500 باعث می‌شود سرور بله دوباره و دوباره همان Update
     * را retry کند، که مشکل اصلی را حل نمی‌کند و فقط لاگ را شلوغ می‌کند.
     */
    void processRawUpdate(String rawBody) {
        try {
            BaleUpdate update = objectMapper.readValue(rawBody, BaleUpdate.class);
            updateHandler.handle(update);
        } catch (Exception e) {
            log.warn("پردازش Update ورودی از بله شکست خورد.", e);
        }
    }
}
