package com.example.roshdeandishe.notification.internal;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * تنظیمات مسیریابِ متن آزاد ({@code roshdeandishe.bot.llm.*}). مقدارِ پیش‌فرضِ هر کلید همان مقدارِ
 * application.yml است تا ساختن آن از یک Environment خالی هم در تست‌ها کار کند.
 * کلید و نشانی هرگز لاگ نمی‌شوند.
 */
@Component
class LlmSettings {

    private static final String PREFIX = "roshdeandishe.bot.llm.";

    private final Environment env;

    LlmSettings(Environment env) {
        this.env = env;
    }

    /** فقط وقتی روشن است که کلید روشن باشد و نشانی و کلید هر دو پر باشند. */
    boolean enabled() {
        return Boolean.parseBoolean(text("enabled", "false")) && !baseUrl().isBlank() && !apiKey().isBlank();
    }

    String baseUrl() {
        return text("base-url", "");
    }

    String apiKey() {
        return text("api-key", "");
    }

    String primaryModel() {
        return text("primary-model", "typesafe/jev-1.13");
    }

    String fallbackModel() {
        return text("fallback-model", "qwen/qwen3-32b");
    }

    String decisionPath() {
        return text("decision-path", "/systemone");
    }

    Duration primaryTimeout() {
        return duration("primary-timeout", Duration.ofSeconds(3));
    }

    Duration fallbackTimeout() {
        return duration("fallback-timeout", Duration.ofSeconds(5));
    }

    double minConfidence() {
        return Double.parseDouble(text("min-confidence", "0.7"));
    }

    int maxTextLength() {
        return Integer.parseInt(text("max-text-length", "500"));
    }

    /** صفر یعنی سقفِ کلیِ روزانه خاموش است. */
    int dailyCapTotal() {
        return Integer.parseInt(text("daily-cap-total", "0"));
    }

    /** سقفِ روزانه‌ی هر نقش؛ کد نقش با حروف بزرگ (مثل {@code TEACHER}) و در نبودنِ کلید، {@code default}. */
    int dailyLimitFor(String roleCode) {
        String own = roleCode == null ? null : env.getProperty(PREFIX + "daily-limit." + roleCode);
        if (own != null && !own.isBlank()) {
            return Integer.parseInt(own.trim());
        }
        return Integer.parseInt(text("daily-limit.default", "30"));
    }

    private String text(String key, String fallback) {
        String v = env.getProperty(PREFIX + key);
        return v == null ? fallback : v.trim();
    }

    /** مقدارهایی مثل {@code 3s}، {@code 500ms}، {@code 2m} یا قالبِ ISO-8601. */
    private Duration duration(String key, Duration fallback) {
        String v = text(key, "").toLowerCase(Locale.ROOT);
        if (v.isEmpty()) {
            return fallback;
        }
        if (v.startsWith("p")) {
            return Duration.parse(v);
        }
        if (v.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(v.substring(0, v.length() - 2).trim()));
        }
        long n = Long.parseLong(v.substring(0, v.length() - 1).trim());
        return switch (v.charAt(v.length() - 1)) {
            case 's' -> Duration.ofSeconds(n);
            case 'm' -> Duration.ofMinutes(n);
            default -> throw new IllegalArgumentException("invalid duration for " + key);
        };
    }
}
