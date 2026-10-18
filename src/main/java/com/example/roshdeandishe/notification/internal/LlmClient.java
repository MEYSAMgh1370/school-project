package com.example.roshdeandishe.notification.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * درگاهِ دسته‌بندیِ متنِ آزاد. مدل فقط مسیریاب است: متنِ شبه‌ناموارهشده و فهرستِ گزینه‌ها
 * را می‌گیرد و یک گزینه برمی‌گرداند. هر خطا با {@link LlmException} گزارش می‌شود.
 */
interface LlmClient {
    ClassificationAnswer classify(ClassificationRequest request);
}

/**
 * @param intentOptions نامِ {@code BotIntent} یا {@code NONE} به راهنمای فارسیِ آن
 * @param targetLabels خالی اگر انتخابی لازم نیست؛ وگرنه برچسب‌ها و در آخر {@code NONE}
 */
record ClassificationRequest(String text, Map<String, String> intentOptions, List<String> targetLabels) {
}

/** {@code target} اگر پرسشِ هدف پرسیده نشده باشد null است. */
record ClassificationAnswer(String intent, double intentConfidence, String target,
                            double targetConfidence, String model) {
}

/** خطای فراخوانیِ مدل؛ پیام هرگز بدنه‌ی درخواست/پاسخ یا کلید را در بر ندارد. */
final class LlmException extends RuntimeException {
    LlmException(String message) {
        super(message);
    }

    LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}

/** منابعِ مشترکِ پرامپت برای همه‌ی آداپتورهای مدل. */
final class LlmPrompts {
    private static final String BASE = "/bot/llm/";

    private LlmPrompts() {
    }

    static String instructions() {
        try (BufferedReader r = new BufferedReader(reader("instructions.txt"))) {
            return r.lines().collect(Collectors.joining("\n")).strip();
        } catch (IOException e) {
            throw new IllegalStateException("instructions.txt unreadable", e);
        }
    }

    /** به ترتیبِ {@link BotIntent} و {@code NONE} در آخر. */
    static Map<String, String> intentHints() {
        Properties p = new Properties();
        try (Reader r = reader("intent-hints.properties")) {
            p.load(r);
        } catch (IOException e) {
            throw new IllegalStateException("intent-hints.properties unreadable", e);
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (BotIntent i : BotIntent.values()) {
            out.put(i.name(), required(p, i.name()));
        }
        out.put("NONE", required(p, "NONE"));
        return out;
    }

    private static String required(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("missing intent hint: " + key);
        }
        return v.strip();
    }

    private static Reader reader(String name) {
        InputStream in = LlmPrompts.class.getResourceAsStream(BASE + name);
        if (in == null) {
            throw new IllegalStateException("missing resource: " + name);
        }
        return new InputStreamReader(in, StandardCharsets.UTF_8);
    }
}
