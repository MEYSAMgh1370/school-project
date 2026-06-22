package com.example.roshdeandishe.iam.shared;

/**
 * یک اکسپشن عمومی برای کل ماژول iam که payload آن یک {@link IamError} است.
 * به‌جای داشتن کلاس Exception جدا برای هر نوع خطا (مثل
 * ModuleNotEnabledException قبلی)، فقط همین یک کلاس پرتاب می‌شود و
 * {@link GlobalExceptionHandler} از روی نوع {@code error()} تشخیص می‌دهد
 * چه پاسخی بسازد.
 */
public final class IamException extends RuntimeException {

    private final IamError error;

    public IamException(IamError error) {
        super(describe(error));
        this.error = error;
    }

    public IamError error() {
        return error;
    }

    private static String describe(IamError error) {
        return switch (error) {
            case IamError.ModuleNotEntitled e ->
                    "ماژول '" + e.moduleCode() + "' برای context داده‌شده فعال نیست: " + e.reason();
            case IamError.ModuleNotFound e ->
                    "ماژول '" + e.moduleCode() + "' در سیستم تعریف نشده است.";
            case IamError.MembershipNotFound e ->
                    "کاربر " + e.userId() + " عضویتی در مدرسه " + e.schoolId() + " ندارد.";
            case IamError.UserNotFound e ->
                    "کاربر یافت نشد" + (e.userId() != null ? " (شناسه: " + e.userId() + ")" : ".");
        };
    }
}
