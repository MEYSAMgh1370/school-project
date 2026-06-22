package com.example.roshdeandishe.iam.shared;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * مدیریت متمرکز اکسپشن‌های مشترک، بر اساس {@link ProblemDetail} (RFC 7807)
 * به‌جای پاسخ‌های دستی Map-based نسخه‌ی قبلی.
 * <p>
 * نکته‌ی کلیدی: {@code handleIamException} با یک switch expression روی
 * {@link IamError} (که sealed است) بدون داشتن default-branch کامپایل
 * می‌شود. یعنی اگر فردا یک نوع خطای جدید به IamError اضافه شود، همین متد
 * در زمان build خطا می‌دهد تا حتماً برایش یک پاسخ مشخص شود - دیگر امکان
 * "یادم رفت این حالت را handle کنم" به‌صورت سایلنت وجود ندارد.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String ERROR_BASE_URI = "https://roshdeandishe.example.com/errors/";

    @ExceptionHandler(IamException.class)
    public ProblemDetail handleIamException(IamException ex) {
        return switch (ex.error()) {
            case IamError.ModuleNotEntitled e -> problem(
                    HttpStatus.FORBIDDEN, "module-not-entitled",
                    "ماژول فعال نیست", e.reason()
            );
            case IamError.ModuleNotFound e -> problem(
                    HttpStatus.INTERNAL_SERVER_ERROR, "module-not-found",
                    "ماژول تعریف نشده",
                    "ماژول '" + e.moduleCode() + "' در جدول iam.modules ثبت نشده است."
            );
            case IamError.MembershipNotFound e -> problem(
                    HttpStatus.FORBIDDEN, "membership-not-found",
                    "عضویت یافت نشد",
                    "کاربر " + e.userId() + " عضویتی در مدرسه " + e.schoolId() + " ندارد."
            );
            case IamError.UserNotFound e -> problem(
                    HttpStatus.UNAUTHORIZED, "user-not-found",
                    "کاربر یافت نشد",
                    "کاربر احراز هویت‌شده‌ی فعلی در سیستم پیدا نشد."
            );
        };
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        // معمولاً به این معنی است که TenantContext مقداردهی نشده (هدر X-School-ID نبوده)
        return problem(
                HttpStatus.BAD_REQUEST, "tenant-context-missing",
                "Context نامعتبر", ex.getMessage()
        );
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "خطای داخلی سرور", "خطای غیرمنتظره‌ای رخ داده است."
        );
    }

    private ProblemDetail problem(HttpStatus status, String typeSlug, String title, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setType(URI.create(ERROR_BASE_URI + typeSlug));
        return problemDetail;
    }
}
