package com.example.roshdeandishe.bale.internal;

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
        BaleMessage message
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BaleMessage(
            @JsonProperty("message_id") Long messageId,
            BaleChat chat,
            String text,
            BaleContact contact
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BaleChat(
            Long id
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BaleContact(
            @JsonProperty("phone_number") String phoneNumber
    ) {
    }
}
