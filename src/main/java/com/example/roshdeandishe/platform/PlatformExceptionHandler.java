package com.example.roshdeandishe.platform;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    /**
     * {@code NoResourceFoundException} (که {@code ResourceHttpRequestHandler}
     * برای هر فایل استاتیک نامعتبر/گم‌شده زیر {@code /css/**}، {@code /js/**}،
     * {@code /webjars/**} پرتاب می‌کند - مثلاً یک درخواست {@code .map} برای
     * سورس‌مپ که هیچ‌وقت وجود نداشته) بدون این متد به handleUnhandled زیر
     * می‌افتاد و ۴۰۴ صحیح آن به ۵۰۰ عمومی تبدیل می‌شد - چون
     * {@code @ExceptionHandler(Exception.class)} همه‌چیز را می‌گیرد، حتی
     * Exceptionهایی که خودشان از قبل کد وضعیت درست را می‌دانند. باگی که روی
     * هر صفحه‌ای که Alpine/Bulma را از WebJar بارگذاری می‌کند تکرار می‌شد
     * (مرورگرها/DevTools همیشه فایل sourcemap را درخواست می‌کنند)، نه فقط
     * صفحه‌ی خاصی؛ اینجا thrown شده pathش را هم دارد، همان را در پیام می‌گذاریم.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFound(NoResourceFoundException ex) {
        return ProblemDetailFactory.problem(
                HttpStatus.NOT_FOUND, "resource-not-found",
                "منبع یافت نشد", "فایلی در مسیر '" + ex.getResourcePath() + "' وجود ندارد."
        );
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnhandled(Exception ex) {
        return ProblemDetailFactory.problem(
                HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "خطای داخلی سرور", "خطای غیرمنتظره‌ای رخ داده است."
        );
    }
}
