package com.example.roshdeandishe.iam;

/**
 * بافتی که در آن دسترسی به یک ماژول بررسی می‌شود. این تعیین می‌کند
 * {@link AccessDecisionService} (که در iam.internal است) باید کدام منبع
 * Entitlement را بررسی کند:
 * <ul>
 *     <li>{@link SchoolContext} - بررسی در سطح یک مدرسه‌ی خاص (B2B)</li>
 *     <li>{@link PlatformContext} - بدون context مدرسه‌ای، برای ماژول‌های
 *     USER-scoped یا PLATFORM-scoped (B2C / عمومی)</li>
 * </ul>
 * به‌عنوان sealed interface تعریف شده تا هر جا روی این نوع switch می‌زنیم
 * (مثلاً در AccessDecisionService) کامپایلر مطمئن شود همه‌ی حالت‌ها پوشش
 * داده شده‌اند.
 */
public sealed interface AccessContext {

    record SchoolContext(Long schoolId) implements AccessContext {
    }

    record PlatformContext() implements AccessContext {
    }
}
