package com.example.roshdeandishe.iam.shared;

/**
 * نگهدارنده‌ی Context چندمستأجری (Multi-Tenancy) در سطح Thread جاری.
 * <p>
 * مقدار {@code schoolId} توسط {@code TenantResolverFilter} از هدر {@code X-School-ID}
 * استخراج و در ابتدای پردازش هر Request مقداردهی می‌شود، و در پایان Request
 * (در یک finally block) حتماً باید پاک شود تا نشتی بین Threadهای استخر شده
 * (Thread Pool Leak) رخ ندهد.
 * <p>
 * در پردازش‌های Async (مثلاً Event Listenerهای {@code @Async}) باید مقدار
 * {@code schoolId} از payload رویداد بازخوانی و دوباره با {@link #set(Long)}
 * در Thread جدید قرار داده شود، چون ThreadLocal بین Threadها به اشتراک گذاشته نمی‌شود.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_SCHOOL_ID = new ThreadLocal<>();

    private TenantContext() {
        // Utility class - جلوگیری از instantiate شدن
    }

    /**
     * مقداردهی شناسه مدرسه برای Thread جاری.
     */
    public static void set(Long schoolId) {
        CURRENT_SCHOOL_ID.set(schoolId);
    }

    /**
     * بازگرداندن شناسه مدرسه فعلی.
     *
     * @throws IllegalStateException اگر هیچ tenant context‌ای مقداردهی نشده باشد
     */
    public static Long get() {
        Long schoolId = CURRENT_SCHOOL_ID.get();
        if (schoolId == null) {
            throw new IllegalStateException(
                    "TenantContext مقداردهی نشده است. هدر X-School-ID ارسال نشده یا " +
                    "این کد در یک Thread جدید (Async) بدون بازیابی Context اجرا شده است."
            );
        }
        return schoolId;
    }

    /**
     * نسخه‌ی امن که در صورت نبود context، خطا نمی‌دهد و null برمی‌گرداند.
     * مناسب برای لاگ‌گیری یا کدهایی که Tenant اختیاری است.
     */
    public static Long getOrNull() {
        return CURRENT_SCHOOL_ID.get();
    }

    public static boolean isSet() {
        return CURRENT_SCHOOL_ID.get() != null;
    }

    /**
     * پاک‌سازی Context. حتماً باید در finally block فیلتر یا انتهای پردازش Async صدا زده شود.
     */
    public static void clear() {
        CURRENT_SCHOOL_ID.remove();
    }
}
