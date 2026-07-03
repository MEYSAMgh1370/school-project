package com.example.roshdeandishe.platform;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Fallback نهایی برای هر Exceptionی که توسط handler مختص هیچ ماژولی
 * (iam.shared.GlobalExceptionHandler, academic.shared.GlobalExceptionHandler, ...)
 * پوشش داده نشده است. عمداً در platform قرار دارد، نه در iam - چون این
 * مسئولیت متعلق به هیچ ماژول دامنه‌ای خاصی نیست.
 * <p>
 * {@code @Order} با مقدار بزرگ (اولویت پایین) تنظیم شده تا Spring همیشه
 * اول handlerهای دقیق‌تر هر ماژول را امتحان کند و این کلاس فقط زمانی اجرا
 * شود که هیچ‌کدام از آن‌ها Exception را نپوشانده باشند.
 */
@RestControllerAdvice
@Order(Integer.MAX_VALUE)
public class PlatformExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnhandled(Exception ex) {
        return ProblemDetailFactory.problem(
                HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "خطای داخلی سرور", "خطای غیرمنتظره‌ای رخ داده است."
        );
    }
}
