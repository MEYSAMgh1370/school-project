package com.example.roshdeandishe.notification.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * مرحله‌ی بیرون از تراکنشِ متن آزاد: سقف روزانه، نشانگرِ «در حال نوشتن»، دسته‌بندی با مدل، اعتبارسنجی و
 * سپردنِ پاسخ به {@link BaleUpdateHandler#answerFreeText} (که تراکنشِ خودش را دارد). عمداً تراکنشی نیست تا
 * هیچ اتصالِ پایگاه‌داده‌ای در طولِ فراخوانیِ مدل باز نماند. به مدل فقط متنِ شبه‌نام‌شده، نامِ intentهای مجاز
 * و برچسب‌ها می‌رسد. هر خطای دسته‌بندی (نه فقط {@link LlmException}) به «متوجه نشدم» و منو می‌رسد. لاگ فقط
 * intent، نتیجه، مدل و زمان است؛ هرگز متن یا برچسب.
 */
@Component
class FreeTextResponder {

    private static final Logger log = LoggerFactory.getLogger(FreeTextResponder.class);

    private static final String NONE = "NONE";
    private static final String TYPING = "typing";

    private final BaleUpdateHandler handler;
    private final LlmClient llmClient;
    private final LlmQuota quota;
    private final LlmSettings settings;
    private final BaleClient baleClient;
    private final BotTokens botTokens;

    FreeTextResponder(BaleUpdateHandler handler, LlmClient llmClient, LlmQuota quota, LlmSettings settings,
                      BaleClient baleClient, BotTokens botTokens) {
        this.handler = handler;
        this.llmClient = llmClient;
        this.quota = quota;
        this.settings = settings;
        this.baleClient = baleClient;
        this.botTokens = botTokens;
    }

    void respond(FreeTextRequest request) {
        if (!quota.tryAcquire(request.userId(), request.activeRoleCodes())) {
            handler.answerLimitReached(request);
            log.info("bot llm: intent={} outcome={} model={} latencyMs={}", NONE, "LIMIT", "-", 0);
            return;
        }
        sendTyping(request);

        Optional<ClassificationValidator.Resolved> resolved = Optional.empty();
        String outcome;
        String model = "-";
        long start = System.nanoTime();
        try {
            List<String> targetLabels = targetLabels(request);
            ClassificationAnswer answer = llmClient.classify(
                    new ClassificationRequest(request.text(), intentOptions(request), targetLabels));
            if (answer != null && answer.model() != null) {
                model = answer.model();
            }
            resolved = ClassificationValidator.validate(answer, request.allowed(), targetLabels,
                    settings.minConfidence());
            outcome = resolved.isPresent() ? "OK" : "FALLBACK";
        } catch (LlmException e) {
            outcome = "FALLBACK";
        } catch (RuntimeException e) {
            // نه پیامِ خطا و نه stack trace: ممکن است بخشی از درخواست یا پاسخ را در خود داشته باشد
            outcome = "ERROR";
        }
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        String intent = resolved.map(r -> r.intent().name()).orElse(NONE);
        try {
            handler.answerFreeText(request, resolved);
        } finally {
            log.info("bot llm: intent={} outcome={} model={} latencyMs={}", intent, outcome, model, latencyMs);
        }
    }

    /** best-effort؛ شکستِ آن نباید پرسش را بی‌پاسخ بگذارد. */
    private void sendTyping(FreeTextRequest request) {
        try {
            baleClient.sendChatAction(botTokens.tokenFor(request.bot()), request.chatId(), TYPING);
        } catch (RuntimeException e) {
            log.debug("bot llm: typing indicator failed ({})", e.getClass().getSimpleName());
        }
    }

    /** راهنمای intentهای مجازِ همین کاربر به ترتیبِ {@link BotIntent}، و {@code NONE} در آخر. */
    private static Map<String, String> intentOptions(FreeTextRequest request) {
        Map<String, String> options = new LinkedHashMap<>();
        LlmPrompts.intentHints().forEach((name, hint) -> {
            if (NONE.equals(name) || request.allowed().stream().anyMatch(i -> i.name().equals(name))) {
                options.put(name, hint);
            }
        });
        return options;
    }

    /** پرسشِ هدف فقط وقتی دست‌کم دو برچسب هست؛ وگرنه روتر خودش (تک‌هدف یا دکمه) تصمیم می‌گیرد. */
    private static List<String> targetLabels(FreeTextRequest request) {
        if (request.labelToStudentId().size() < 2) {
            return List.of();
        }
        List<String> labels = new ArrayList<>(request.labelToStudentId().keySet());
        labels.add(NONE);
        return List.copyOf(labels);
    }
}
