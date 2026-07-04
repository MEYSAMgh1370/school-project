package com.example.roshdeandishe.iam.shared.exception;

import com.example.roshdeandishe.platform.ProblemDetailFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * فقط مسئول routing است: IamException را به IamProblemResolver می‌دهد.
 * (قبلاً GlobalExceptionHandler نام داشت؛ برای رفع تداخل نام Bean بین
 * ماژول‌ها، به IamExceptionHandler تغییر نام یافت.)
 * منطق نگاشت خطا -> ProblemDetail اینجا نیست (در IamProblemResolver است)
 * تا این کلاس کوچک و قابل‌تست بماند و resolver بدون نیاز به MVC context
 * مستقلاً تست شود.
 * <p>
 * {@code @Order(0)} صریحاً تنظیم شده تا قبل از
 * {@code PlatformExceptionHandler} (که {@code @Order(Integer.MAX_VALUE)}
 * دارد) اجرا شود. بدون این، هر دو روی مقدار پیش‌فرض
 * {@code Ordered.LOWEST_PRECEDENCE} (که خودش Integer.MAX_VALUE است) قرار
 * می‌گرفتند و کدام handler برای IamException اجرا شود به ترتیب نامعین
 * ثبت beanها بستگی داشت.
 * <p>
 * ⚠️ نام Bean عمداً از طریق {@code @Component("iamGlobalExceptionHandler")}
 * تنظیم شده، نه {@code @RestControllerAdvice("...")}. آرگومان
 * {@code value()} در {@code @ControllerAdvice} (و به تبع آن
 * {@code @RestControllerAdvice}) با {@code @AliasFor("basePackages")} به
 * {@code basePackages} نگاشت می‌شود - یعنی {@code String[]}، نه نام Bean.
 * پاس دادن یک رشته به آن این Advice را فقط به کنترلرهای زیرِ پکیجی به نام
 * دقیقاً همان رشته محدود می‌کند (که هیچ کنترلر واقعی‌ای در آن نیست)،
 * و باعث می‌شود این handler هرگز روی هیچ کنترلری اعمال نشود - دقیقاً همان
 * باگی که PlatformExceptionHandler (بدون هیچ محدودیتی) را همیشه تنها
 * catch-all واقعی می‌کرد، صرف‌نظر از @Order. تأیید شده با اجرای واقعی HTTP.
 */
@RestControllerAdvice
@Component("iamGlobalExceptionHandler")
@Order(0)
@RequiredArgsConstructor
public class IamExceptionHandler {

    private final IamProblemResolver resolver;

    @ExceptionHandler(IamException.class)
    public ProblemDetail handleIamException(IamException ex) {
        return resolver.resolve(ex.error());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        // معمولاً به این معنی است که TenantContext مقداردهی نشده (هدر X-School-ID نبوده)
        return ProblemDetailFactory.problem(
                HttpStatus.BAD_REQUEST, "tenant-context-missing",
                "Context نامعتبر", ex.getMessage()
        );
    }
}
