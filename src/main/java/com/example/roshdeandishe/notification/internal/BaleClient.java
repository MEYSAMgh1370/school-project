package com.example.roshdeandishe.notification.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper json = new ObjectMapper();

    BaleClient(@Value("${roshdeandishe.bale.api-base-url}") String apiBaseUrl) {
        // بدون timeout یک قطعیِ بله می‌توانست thread زمان‌بندی را بی‌نهایت نگه دارد
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().baseUrl(apiBaseUrl).requestFactory(requestFactory).build();
    }

    boolean sendMessage(String token, Long chatId, String text) {
        return deliver(token, chatId, text) == DeliveryResult.SENT;
    }

    /**
     * ارسال با نتیجه‌ی دقیق: بدون توکن یا پاسخ 4xx (غیر از 429) = UNDELIVERABLE (تلاش دوباره فایده ندارد، مثلاً
     * کاربر ربات را بسته)، و 5xx، 429 یا خطای شبکه/تایم‌اوت = TEMPORARY_FAILURE. متن پیام هرگز
     * لاگ نمی‌شود؛ برای خطاها فقط وضعیت HTTP.
     */
    DeliveryResult deliver(String token, Long chatId, String text) {
        return deliverDetailed(token, chatId, text).result();
    }

    /** نتیجه‌ی ارسال به‌همراه کد HTTP ({@code httpStatus} صفر یعنی پاسخی دریافت نشد). */
    record SendResult(DeliveryResult result, int httpStatus) {
    }

    /** همان {@link #deliver} با کد HTTP؛ کد برای ثبت وضعیتِ ربات (مثلاً توکن باطل‌شده) لازم است. */
    SendResult deliverDetailed(String token, Long chatId, String text) {
        if (token == null || token.isBlank()) {
            log.warn("توکن این بات در دسترس نیست - ارسال انجام نشد (chatId={}).", chatId);
            return new SendResult(DeliveryResult.UNDELIVERABLE, 0);
        }

        try {
            restClient.post()
                    .uri("/bot{token}/sendMessage", token)
                    .body(new SendMessageRequest(chatId, text, null))
                    .retrieve()
                    .toBodilessEntity();
            return new SendResult(DeliveryResult.SENT, 200);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            boolean permanent = status >= 400 && status < 500 && status != 429;
            log.warn("ارسال پیام بله به chatId={} شکست خورد (HTTP {}).", chatId, status);
            return new SendResult(permanent ? DeliveryResult.UNDELIVERABLE : DeliveryResult.TEMPORARY_FAILURE, status);
        } catch (RestClientException e) {
            log.warn("ارسال پیام بله به chatId={} شکست خورد ({}).", chatId, failureKind(e));
            return new SendResult(DeliveryResult.TEMPORARY_FAILURE, 0);
        }
    }

    record BotInfo(long id, String username) {
    }

    /** نتیجه‌ی {@link #getMe}: ربات پیدا شد، توکن نامعتبر است، یا بله در دسترس نیست (تلاش دوباره منطقی است). */
    sealed interface BotInfoResult {
        record Found(BotInfo bot) implements BotInfoResult {
        }

        record InvalidToken() implements BotInfoResult {
        }

        record Unreachable() implements BotInfoResult {
        }
    }

    /** اعتبارسنجی توکن و دریافت هویت ربات. 401/404 یا ok:false = توکن نامعتبر؛ خطای شبکه، 5xx و 429 = در دسترس نیست. */
    BotInfoResult getMe(String token) {
        if (token == null || token.isBlank()) {
            return new BotInfoResult.InvalidToken();
        }
        try {
            String body = restClient.get().uri("/bot{token}/getMe", token).retrieve().body(String.class);
            JsonNode root = json.readTree(body);
            JsonNode result = root == null ? null : root.get("result");
            if (root == null || !root.path("ok").asBoolean(false) || result == null || !result.hasNonNull("id")) {
                return new BotInfoResult.InvalidToken();
            }
            return new BotInfoResult.Found(new BotInfo(result.get("id").asLong(), result.path("username").asText("")));
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("getMe بله شکست خورد (HTTP {}).", status);
            boolean temporary = status >= 500 || status == 429;
            return temporary ? new BotInfoResult.Unreachable() : new BotInfoResult.InvalidToken();
        } catch (RestClientException e) {
            log.warn("getMe بله شکست خورد ({}).", failureKind(e));
            return new BotInfoResult.Unreachable();
        } catch (java.io.IOException e) {
            log.warn("پاسخ getMe بله قابل خواندن نبود ({}).", e.getClass().getSimpleName());
            return new BotInfoResult.Unreachable();
        }
    }

    /** ثبت وب‌هوک؛ فقط با HTTP 200 و ok:true مقدار true برمی‌گردد. */
    boolean setWebhook(String token, String url) {
        if (token == null || token.isBlank()) {
            log.warn("setWebhook بله بدون توکن فراخوانی شد.");
            return false;
        }
        return postForOk(token, "setWebhook", new SetWebhookRequest(url));
    }

    /** حذف وب‌هوک (best-effort)؛ true فقط با HTTP 200 و ok:true. */
    boolean deleteWebhook(String token) {
        if (token == null || token.isBlank()) {
            log.warn("deleteWebhook بله بدون توکن فراخوانی شد.");
            return false;
        }
        return postForOk(token, "deleteWebhook", java.util.Map.of());
    }

    /**
     * نشانگرِ «در حال نوشتن…» (best-effort)؛ شکست فقط با نوعِ خطا لاگ می‌شود و جریانِ پاسخ را متوقف نمی‌کند.
     */
    boolean sendChatAction(String token, Long chatId, String action) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return postForOk(token, "sendChatAction", new ChatActionRequest(chatId, action));
    }

    private boolean postForOk(String token, String method, Object body) {
        try {
            String response = restClient.post()
                    .uri("/bot{token}/{method}", token, method)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = json.readTree(response);
            return root != null && root.path("ok").asBoolean(false);
        } catch (RestClientException e) {
            log.warn("{} بله شکست خورد ({}).", method, failureKind(e));
            return false;
        } catch (java.io.IOException e) {
            log.warn("پاسخ {} بله قابل خواندن نبود ({}).", method, e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * پیام همراه با یک دکمه‌ی «اشتراک‌گذاری شماره تلفن» (request_contact) -
     * قدم اول لینک کردن یک chat جدید به یک User موجود.
     */
    boolean sendContactRequest(String token, Long chatId, String text) {
        if (token == null || token.isBlank()) {
            log.warn("توکن این بات در دسترس نیست - درخواست شماره تلفن به chatId={} ارسال نشد.", chatId);
            return false;
        }

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup(
                List.of(List.of(new KeyboardButton("اشتراک‌گذاری شماره تلفن", true))),
                true
        );

        try {
            restClient.post()
                    .uri("/bot{token}/sendMessage", token)
                    .body(new SendMessageRequest(chatId, text, keyboard))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            log.warn("ارسال درخواست شماره تلفن به chatId={} شکست خورد ({}).", chatId, failureKind(e));
            return false;
        }
    }

    /**
     * پیام همراه با کیبورد پاسخِ پایدار (منوی همیشه‌نمایان): resize_keyboard=true و one_time_keyboard=false؛ هر
     * عنصر rows یک ردیف از متن دکمه‌هاست. بدون توکن هیچ تماسی برقرار نمی‌شود.
     */
    boolean sendMenu(String token, Long chatId, String text, List<List<String>> rows) {
        if (token == null || token.isBlank()) {
            log.warn("توکن این بات در دسترس نیست - پیام منو به chatId={} ارسال نشد.", chatId);
            return false;
        }

        MenuKeyboardMarkup markup = new MenuKeyboardMarkup(rows, true, false);

        try {
            restClient.post()
                    .uri("/bot{token}/sendMessage", token)
                    .body(new SendMessageRequest(chatId, text, markup))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            log.warn("ارسال پیام منو به chatId={} شکست خورد ({}).", chatId, failureKind(e));
            return false;
        }
    }

    /** سقف callback_data در Bot API (تلگرام/بله): ۱ تا ۶۴ بایت UTF-8. */
    private static final int MAX_CALLBACK_DATA_BYTES = 64;

    /** یک دکمه‌ی اینلاین؛ callbackData در کلیک به شکل callback_query.data برمی‌گردد. */
    record InlineButton(String text, String callbackData) {
    }

    /**
     * پیام همراه با کیبورد اینلاین (reply_markup.inline_keyboard). بله callbackData را فقط ۱ تا ۶۴ بایت UTF-8
     * می‌پذیرد، پس callbackData خالی، تهی یا بلندتر قبل از هر تماس HTTP با IllegalArgumentException (خطای
     * برنامه‌نویس) رد می‌شود.
     */
    boolean sendMessage(String token, Long chatId, String text, List<List<InlineButton>> inlineKeyboard) {
        for (List<InlineButton> row : inlineKeyboard) {
            for (InlineButton button : row) {
                String data = button.callbackData();
                int bytes = data == null ? 0 : data.getBytes(StandardCharsets.UTF_8).length;
                if (bytes < 1 || bytes > MAX_CALLBACK_DATA_BYTES) {
                    throw new IllegalArgumentException("callbackData دکمه‌ی «" + button.text() + "» باید ۱ تا "
                            + MAX_CALLBACK_DATA_BYTES + " بایت باشد، نه " + bytes);
                }
            }
        }
        if (token == null || token.isBlank()) {
            log.warn("توکن این بات در دسترس نیست - پیام کیبورددار به chatId={} ارسال نشد.", chatId);
            return false;
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(inlineKeyboard.stream()
                .map(row -> row.stream().map(b -> new InlineButtonJson(b.text(), b.callbackData())).toList())
                .toList());

        try {
            restClient.post()
                    .uri("/bot{token}/sendMessage", token)
                    .body(new SendMessageRequest(chatId, text, markup))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            log.warn("ارسال پیام کیبورددار به chatId={} شکست خورد ({}).", chatId, failureKind(e));
            return false;
        }
    }

    /** پاسخ به callback_query تا spinner دکمه در کلاینت بله قطع شود. */
    boolean answerCallbackQuery(String token, String callbackQueryId) {
        if (token == null || token.isBlank()) {
            log.warn("توکن این بات در دسترس نیست - پاسخ callback_query ارسال نشد.");
            return false;
        }

        try {
            restClient.post()
                    .uri("/bot{token}/answerCallbackQuery", token)
                    .body(new AnswerCallbackQueryRequest(callbackQueryId))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            log.warn("پاسخ به callback_query شکست خورد ({}).", failureKind(e));
            return false;
        }
    }

    /** هر sendMessage از اینجا می‌گذرد، پس متن همه‌ی پیام‌ها بدون نویسه‌ی Markdown فرستاده می‌شود. */
    /**
     * شرح امنِ یک شکست برای لاگ: فقط نوع خطا و در صورت وجود وضعیت HTTP. عمداً نه خود exception و نه پیامش، چون
     * پیام exceptionهای شبکه‌ی Spring آدرس کامل درخواست را دارد و توکن بات بخشی از آن آدرس است ({@code /bot<token>/}).
     */
    static String failureKind(RestClientException e) {
        if (e instanceof RestClientResponseException response) {
            return "HTTP " + response.getStatusCode().value();
        }
        return e.getClass().getSimpleName();
    }

    private record SendMessageRequest(
            @JsonProperty("chat_id") Long chatId,
            String text,
            @JsonProperty("reply_markup") Object replyMarkup
    ) {
        SendMessageRequest {
            text = PersianText.withoutMarkdown(text);
        }
    }

    private record SetWebhookRequest(String url) {
    }

    private record ChatActionRequest(@JsonProperty("chat_id") Long chatId, String action) {
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

    private record MenuKeyboardMarkup(
            @JsonProperty("keyboard") List<List<String>> keyboard,
            @JsonProperty("resize_keyboard") boolean resizeKeyboard,
            @JsonProperty("one_time_keyboard") boolean oneTimeKeyboard
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
