package com.example.roshdeandishe.iam.shared;

/**
 * مدل خطای دامنه‌محور ماژول iam، به‌جای رشته‌های خام یا اکسپشن‌های پراکنده.
 * هر حالت یک {@code record} با داده‌ی دقیق لازم برای ساخت پاسخ
 * {@link org.springframework.http.ProblemDetail} است.
 * <p>
 * چون این یک sealed interface است، {@link GlobalExceptionHandler} می‌تواند
 * با یک switch expression بدون نیاز به default-branch، تمام حالت‌ها را
 * exhaustively پوشش دهد - اگر فردا یک نوع خطای جدید اضافه شود، کامپایلر
 * تمام جاهایی که این switch استفاده شده را به خطا می‌دهد تا حتماً پوشش داده شود.
 */
public sealed interface IamError {

    record ModuleNotEntitled(String moduleCode, Long schoolId, String reason) implements IamError {
    }

    record ModuleNotFound(String moduleCode) implements IamError {
    }

    record MembershipNotFound(Long userId, Long schoolId) implements IamError {
    }

    record UserNotFound(Long userId) implements IamError {
    }
}
