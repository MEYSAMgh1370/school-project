package com.example.roshdeandishe.notification.shared;

/**
 * مدل خطای دامنه‌محور ماژول notification (openspec/changes/bale-school-bots.md).
 */
public sealed interface NotificationError {

    /** توکن خالی است یا بله آن را نپذیرفت. */
    record BotTokenInvalid() implements NotificationError {
    }

    /** این ربات برای مدرسه‌ی دیگری ثبت شده است. */
    record BotAlreadyRegistered() implements NotificationError {
    }

    /** بله در دسترس نیست یا وب‌هوک تنظیم نشد؛ تلاش دوباره منطقی است. */
    record BaleUnavailable() implements NotificationError {
    }

    /** کلید رمزنگاری توکن پیکربندی نشده است. */
    record BotEncryptionUnavailable() implements NotificationError {
    }

    /** سقف ۱۰ تلاش ثبتِ ربات در ساعت برای این مدرسه پر شده است. */
    record TooManyAttempts() implements NotificationError {
    }

    /** مدرسه رباتی ثبت‌شده ندارد. */
    record SchoolBotNotFound(Long schoolId) implements NotificationError {
    }
}
