package com.example.roshdeandishe.iam;

/**
 * نتیجه‌ی بررسی "آیا کاربر X به ماژول Y در context Z دسترسی دارد؟".
 * این نوع جایگزین boolean ساده‌ی متد قبلی {@code isModuleEnabled} شده،
 * چون {@code reason} برای لاگ‌گیری/دیباگ و پیام خطای دقیق به کاربر لازم است.
 */
public sealed interface AccessDecision {

    record Granted(String reason) implements AccessDecision {
    }

    record Denied(String reason) implements AccessDecision {
    }

    default boolean isGranted() {
        return this instanceof Granted;
    }
}
