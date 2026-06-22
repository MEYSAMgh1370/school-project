package com.example.roshdeandishe.academic;

/**
 * رویدادی که هنگام ثبت‌نام موفق یک دانش‌آموز جدید منتشر می‌شود.
 * <p>
 * این رویداد در پکیج بیرونی (نه internal) قرار دارد چون باید توسط سایر
 * ماژول‌ها (مثلاً finance برای صدور فاکتور شهریه) قابل subscribe باشد.
 * <p>
 * فیلد {@code schoolId} عمداً در payload رویداد قرار داده شده است؛ چون
 * Listenerهای {@code @Async} در Thread جدیدی اجرا می‌شوند و
 * {@link com.example.roshdeandishe.iam.shared.TenantContext} (که ThreadLocal
 * است) در آن Thread خالی خواهد بود. بدون این فیلد، Listener نمی‌تواند
 * Tenant Context را بازسازی کند.
 */
public record StudentEnrolledEvent(
        Long schoolId,
        Long studentId,
        String gradeLevel
) {
}
