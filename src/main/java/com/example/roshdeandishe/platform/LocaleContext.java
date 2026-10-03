package com.example.roshdeandishe.platform;

import java.util.List;
import java.util.Locale;

/**
 * نگهدارنده‌ی زبان (Locale) حل‌شده‌ی Request جاری، در سطح Thread.
 * <p>
 * مقدار توسط {@code LocaleResolverFilter} از هدر {@code Accept-Language}
 * استخراج و در ابتدای پردازش هر Request مقداردهی می‌شود، و در پایان Request
 * (در یک finally block) حتماً باید پاک شود تا نشتی بین Threadهای استخر شده
 * (Thread Pool Leak) رخ ندهد.
 * <p>
 * تفاوت مهم با {@link TenantContext}: خواندن این مقدار هرگز خطا نمی‌دهد و در
 * صورت نبود مقدار، زبان پیش‌فرض (فارسی) برمی‌گردد. دلیل: {@code OncePerRequestFilter}
 * به‌صورت پیش‌فرض روی ERROR dispatch اجرا نمی‌شود، پس صفحه‌ی {@code /error}
 * (که با JTE رندر می‌شود) این نگهدارنده را بدون هیچ مقداری می‌خواند - پرتاب
 * خطا در آن نقطه، یک صفحه‌ی خطا را به خطای دوم تبدیل می‌کرد.
 * <p>
 * زبان‌های پشتیبانی‌شده فقط {@code fa} (پیش‌فرض) و {@code en} هستند.
 */
public final class LocaleContext {

    public static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("fa");

    public static final Locale ENGLISH = Locale.forLanguageTag("en");

    /**
     * زبان‌های پشتیبانی‌شده؛ ورودی {@link Locale#lookup} هم هست.
     */
    public static final List<Locale> SUPPORTED_LOCALES = List.of(DEFAULT_LOCALE, ENGLISH);

    private static final ThreadLocal<Locale> CURRENT_LOCALE = new ThreadLocal<>();

    private LocaleContext() {
        // Utility class - جلوگیری از instantiate شدن
    }

    /**
     * مقداردهی زبان برای Thread جاری.
     */
    public static void set(Locale locale) {
        CURRENT_LOCALE.set(locale);
    }

    /**
     * بازگرداندن زبان فعلی؛ در صورت نبود context، زبان پیش‌فرض (فارسی) را
     * برمی‌گرداند و هرگز خطا نمی‌دهد (برخلاف {@link TenantContext#get()}).
     */
    public static Locale getOrDefault() {
        Locale locale = CURRENT_LOCALE.get();
        return locale != null ? locale : DEFAULT_LOCALE;
    }

    /**
     * نسخه‌ی خام که در صورت نبود context، null برمی‌گرداند.
     */
    public static Locale getOrNull() {
        return CURRENT_LOCALE.get();
    }

    public static boolean isSet() {
        return CURRENT_LOCALE.get() != null;
    }

    /**
     * پاک‌سازی Context. حتماً باید در finally block فیلتر یا انتهای پردازش Async صدا زده شود.
     */
    public static void clear() {
        CURRENT_LOCALE.remove();
    }
}
