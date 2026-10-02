package com.example.roshdeandishe.notification.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * کلاینت نازک روی Bot API بله (تعمداً کلون Telegram Bot API - همان شکل
 * {@code https://tapi.bale.ai/bot<token>/METHOD}). ارسال best-effort است،
 * دقیقاً مثل iam/internal/InviteEmailSender: اگر ارسال شکست بخورد (یا
 * botToken هنوز پیکربندی نشده - پیش‌فرض dev)، false برمی‌گردد و لاگ
 * می‌شود، نه exception - جریان صدور فاکتور/رویدادها نباید به دلیل
 * دردسترس‌نبودن بله شکست بخورد.
 * <p>
 * roshdeandishe.bale.bot-token پیش‌فرض خالی دارد؛ در این حالت متدها هیچ
 * تماس HTTPای برقرار نمی‌کنند - فقط لاگ می‌کنند - چون یک توکن واقعی
 * نیازمند ثبت‌نام واقعی ربات در بله است (کانفیگ زمان استقرار، نه کد گم‌شده).
 */
@Component
class BaleClient {

    private static final Logger log = LoggerFactory.getLogger(BaleClient.class);

    private final RestClient restClient;
    private final String botToken;

    BaleClient(
            @Value("${roshdeandishe.bale.api-base-url}") String apiBaseUrl,
            @Value("${roshdeandishe.bale.bot-token}") String botToken
    ) {
        this.restClient = RestClient.builder().baseUrl(apiBaseUrl).build();
        this.botToken = botToken;
    }

    boolean sendMessage(Long chatId, String text) {
        if (botToken == null || botToken.isBlank()) {
            log.warn("roshdeandishe.bale.bot-token پیکربندی نشده - پیام به chatId={} ارسال نشد: {}", chatId, text);
            return false;
        }

        try {
            restClient.post()
                    .uri("/bot{token}/sendMessage", botToken)
                    .body(new SendMessageRequest(chatId, text, null))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            log.warn("ارسال پیام بله به chatId={} شکست خورد.", chatId, e);
            return false;
        }
    }

    /**
     * پیام همراه با یک دکمه‌ی «اشتراک‌گذاری شماره تلفن» (request_contact) -
     * قدم اول لینک کردن یک chat جدید به یک User موجود.
     */
    boolean sendContactRequest(Long chatId, String text) {
        if (botToken == null || botToken.isBlank()) {
            log.warn("roshdeandishe.bale.bot-token پیکربندی نشده - درخواست شماره تلفن به chatId={} ارسال نشد.", chatId);
            return false;
        }

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup(
                List.of(List.of(new KeyboardButton("اشتراک‌گذاری شماره تلفن", true))),
                true
        );

        try {
            restClient.post()
                    .uri("/bot{token}/sendMessage", botToken)
                    .body(new SendMessageRequest(chatId, text, keyboard))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            log.warn("ارسال درخواست شماره تلفن به chatId={} شکست خورد.", chatId, e);
            return false;
        }
    }

    private record SendMessageRequest(
            @JsonProperty("chat_id") Long chatId,
            String text,
            @JsonProperty("reply_markup") ReplyKeyboardMarkup replyMarkup
    ) {
    }

    private record ReplyKeyboardMarkup(
            @JsonProperty("keyboard") List<List<KeyboardButton>> keyboard,
            @JsonProperty("one_time_keyboard") boolean oneTimeKeyboard
    ) {
    }

    private record KeyboardButton(
            String text,
            @JsonProperty("request_contact") boolean requestContact
    ) {
    }
}
