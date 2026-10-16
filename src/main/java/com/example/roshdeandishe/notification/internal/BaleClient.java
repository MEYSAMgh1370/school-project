package com.example.roshdeandishe.notification.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.example.roshdeandishe.notification.NotificationFacade.DeliveryResult;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClientResponseException;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
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

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient restClient;
    private final String botToken;

    BaleClient(
            @Value("${roshdeandishe.bale.api-base-url}") String apiBaseUrl,
            @Value("${roshdeandishe.bale.bot-token}") String botToken
    ) {
        // بدون timeout یک قطعیِ بله می‌توانست thread زمان‌بندی را بی‌نهایت نگه دارد
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().baseUrl(apiBaseUrl).requestFactory(requestFactory).build();
        this.botToken = botToken;
    }

    boolean sendMessage(Long chatId, String text) {
        return deliver(chatId, text) == DeliveryResult.SENT;
    }

    /**
     * ارسال با نتیجه‌ی دقیق: بدون توکن یا پاسخ 4xx (غیر از 429) = UNDELIVERABLE (تلاش دوباره فایده ندارد، مثلاً
     * کاربر ربات را بسته)، و 5xx، 429 یا خطای شبکه/تایم‌اوت = TEMPORARY_FAILURE. متن پیام فقط در حالت
     * «توکن پیکربندی نشده» لاگ می‌شود (رفتار قبلی)؛ برای خطاها فقط وضعیت HTTP.
     */
    DeliveryResult deliver(Long chatId, String text) {
        if (botToken == null || botToken.isBlank()) {
            log.warn("roshdeandishe.bale.bot-token پیکربندی نشده - پیام به chatId={} ارسال نشد: {}", chatId, text);
            return DeliveryResult.UNDELIVERABLE;
        }

        try {
            restClient.post()
                    .uri("/bot{token}/sendMessage", botToken)
                    .body(new SendMessageRequest(chatId, text, null))
                    .retrieve()
                    .toBodilessEntity();
            return DeliveryResult.SENT;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            boolean permanent = status >= 400 && status < 500 && status != 429;
            log.warn("ارسال پیام بله به chatId={} شکست خورد (HTTP {}).", chatId, status);
            return permanent ? DeliveryResult.UNDELIVERABLE : DeliveryResult.TEMPORARY_FAILURE;
        } catch (RestClientException e) {
            log.warn("ارسال پیام بله به chatId={} شکست خورد.", chatId, e);
            return DeliveryResult.TEMPORARY_FAILURE;
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

    /** سقف callback_data در Bot API (تلگرام/بله): ۱ تا ۶۴ بایت UTF-8. */
    private static final int MAX_CALLBACK_DATA_BYTES = 64;

    /** یک دکمه‌ی اینلاین؛ callbackData در کلیک به شکل callback_query.data برمی‌گردد. */
    record InlineButton(String text, String callbackData) {
    }

    /**
     * پیام همراه با کیبورد اینلاین (reply_markup.inline_keyboard). callbackData بیش از ۶۴ بایت UTF-8 را
     * بله رد می‌کند، پس قبل از هر تماس HTTP با IllegalArgumentException (خطای برنامه‌نویس) رد می‌شود.
     */
    boolean sendMessage(Long chatId, String text, List<List<InlineButton>> inlineKeyboard) {
        for (List<InlineButton> row : inlineKeyboard) {
            for (InlineButton button : row) {
                if (button.callbackData().getBytes(StandardCharsets.UTF_8).length > MAX_CALLBACK_DATA_BYTES) {
                    throw new IllegalArgumentException("callbackData بیش از " + MAX_CALLBACK_DATA_BYTES + " بایت است");
                }
            }
        }
        if (botToken == null || botToken.isBlank()) {
            log.warn("roshdeandishe.bale.bot-token پیکربندی نشده - پیام کیبورددار به chatId={} ارسال نشد.", chatId);
            return false;
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(inlineKeyboard.stream()
                .map(row -> row.stream().map(b -> new InlineButtonJson(b.text(), b.callbackData())).toList())
                .toList());

        try {
            restClient.post()
                    .uri("/bot{token}/sendMessage", botToken)
                    .body(new SendMessageRequest(chatId, text, markup))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            log.warn("ارسال پیام کیبورددار به chatId={} شکست خورد.", chatId, e);
            return false;
        }
    }

    /** پاسخ به callback_query تا spinner دکمه در کلاینت بله قطع شود. */
    boolean answerCallbackQuery(String callbackQueryId) {
        if (botToken == null || botToken.isBlank()) {
            log.warn("roshdeandishe.bale.bot-token پیکربندی نشده - پاسخ callback_query ارسال نشد.");
            return false;
        }

        try {
            restClient.post()
                    .uri("/bot{token}/answerCallbackQuery", botToken)
                    .body(new AnswerCallbackQueryRequest(callbackQueryId))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            log.warn("پاسخ به callback_query شکست خورد.", e);
            return false;
        }
    }

    private record SendMessageRequest(
            @JsonProperty("chat_id") Long chatId,
            String text,
            @JsonProperty("reply_markup") Object replyMarkup
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

    private record InlineKeyboardMarkup(
            @JsonProperty("inline_keyboard") List<List<InlineButtonJson>> inlineKeyboard
    ) {
    }

    private record InlineButtonJson(
            String text,
            @JsonProperty("callback_data") String callbackData
    ) {
    }

    private record AnswerCallbackQueryRequest(
            @JsonProperty("callback_query_id") String callbackQueryId
    ) {
    }
}
