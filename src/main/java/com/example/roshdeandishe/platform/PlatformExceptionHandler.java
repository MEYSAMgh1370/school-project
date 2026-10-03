package com.example.roshdeandishe.platform;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

    /**
     * خطاهای کلاینت که خودِ Spring MVC پیش از رسیدن به کنترلر پرتاب می‌کند: بدون این
     * متدها {@code @ExceptionHandler(Exception.class)} پایین همه را به ۵۰۰ تبدیل می‌کرد -
     * مثلاً بدنه‌ی JSON خراب یا مقدارِ ناشناخته‌ی یک enum، پارامتر کوئریِ نامعتبر یا
     * گم‌شده، متد یا Content-Type نامجاز. این‌ها خطای کلاینت‌اند (۴xx)، نه خطای سرور.
     * پیام عمداً جزئیاتِ داخلی (نامِ کلاس، پیامِ Jackson) را لو نمی‌دهد.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ProblemDetailFactory.problem(
                HttpStatus.BAD_REQUEST, "malformed-request-body",
                "بدنه‌ی درخواست نامعتبر است",
                "بدنه‌ی درخواست خوانده نشد؛ قالب JSON و مقدار فیلدها را بررسی کنید."
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleParameterTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ProblemDetailFactory.problem(
                HttpStatus.BAD_REQUEST, "invalid-parameter",
                "پارامتر نامعتبر است",
                "مقدار پارامتر «" + ex.getName() + "» نامعتبر است."
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParameter(MissingServletRequestParameterException ex) {
        return ProblemDetailFactory.problem(
                HttpStatus.BAD_REQUEST, "missing-parameter",
                "پارامتر الزامی ارسال نشده است",
                "پارامتر «" + ex.getParameterName() + "» الزامی است."
        );
    }

    // هدرهای خودِ exception (Allow برای 405، Accept برای 415) باید همراه پاسخ بروند - RFC 9110 آن‌ها را
    // الزامی می‌داند و کلاینت‌های عمومی از همین‌ها متدها/نوع‌های مجاز را می‌فهمند
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(ex.getHeaders()).body(
                ProblemDetailFactory.problem(
                        HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed",
                        "متد درخواست مجاز نیست", "این مسیر متد " + ex.getMethod() + " را نمی‌پذیرد."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).headers(ex.getHeaders()).body(
                ProblemDetailFactory.problem(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-media-type",
                        "نوع محتوا پشتیبانی نمی‌شود", "Content-Type این درخواست پذیرفته نمی‌شود."));
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnhandled(Exception ex) {
        return ProblemDetailFactory.problem(
                HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "خطای داخلی سرور", "خطای غیرمنتظره‌ای رخ داده است."
        );
    }
}
