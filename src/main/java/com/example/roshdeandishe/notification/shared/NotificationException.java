package com.example.roshdeandishe.notification.shared;

public final class NotificationException extends RuntimeException {

    private final NotificationError error;

    public NotificationException(NotificationError error) {
        super(describe(error));
        this.error = error;
    }

    public NotificationError error() {
        return error;
    }

    private static String describe(NotificationError error) {
        return switch (error) {
            case NotificationError.BotTokenInvalid e -> "توکن بات نامعتبر است.";
            case NotificationError.BotAlreadyRegistered e -> "این بات برای مدرسه‌ی دیگری ثبت شده است.";
            case NotificationError.BaleUnavailable e -> "ارتباط با بله برقرار نشد.";
            case NotificationError.BotEncryptionUnavailable e -> "ثبت بات در این محیط پیکربندی نشده است.";
            case NotificationError.TooManyAttempts e -> "تعداد تلاش‌ها بیش از حد مجاز است.";
            case NotificationError.SchoolBotNotFound e -> "برای مدرسه‌ی " + e.schoolId() + " باتی ثبت نشده است.";
        };
    }
}
