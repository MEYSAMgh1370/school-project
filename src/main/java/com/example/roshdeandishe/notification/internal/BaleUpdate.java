package com.example.roshdeandishe.notification.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * شکل JSON یک Update ورودی از وب‌هوک بله. Bot API بله عمداً یک کلون از
 * Telegram Bot API است (همان endpointها، همان اسکیمای Update/Message) -
 * این رکوردها فقط زیرمجموعه‌ی فیلدهایی را می‌گیرند که این ماژول واقعاً
 * استفاده می‌کند؛ {@code @JsonIgnoreProperties(ignoreUnknown = true)} روی
 * هر رکورد یعنی بقیه‌ی فیلدهای Update (که فعلاً به کارمان نمی‌آید) باعث
 * خطای parse نمی‌شوند.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record BaleUpdate(
        @JsonProperty("update_id") Long updateId,
        BaleMessage message,
        @JsonProperty("callback_query") BaleCallbackQuery callbackQuery
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BaleMessage(
            @JsonProperty("message_id") Long messageId,
            BaleUser from,
            BaleChat chat,
            String text,
            BaleContact contact
    ) {
    }

    /** فرستنده‌ی پیام (در پست کانال خالی است)؛ id تا ۵۲ بیت است، پس Long. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record BaleUser(
            Long id
    ) {
    }

    /** type یکی از private / group / channel است. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record BaleChat(
            Long id,
            String type
    ) {
    }

    /** userId (اختیاری) شناسه‌ی کاربر بله‌ی صاحب مخاطب است؛ برای تطبیق با فرستنده. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record BaleContact(
            @JsonProperty("phone_number") String phoneNumber,
            @JsonProperty("user_id") Long userId
    ) {
    }

    /** کلیک روی یک دکمه‌ی اینلاین؛ message پیامی است که دکمه زیر آن بوده (chat همان‌جاست). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record BaleCallbackQuery(
            String id,
            BaleMessage message,
            String data
    ) {
    }
}
