package com.example.roshdeandishe.finance.shared;

import com.example.roshdeandishe.platform.ProblemDetailFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * همان الگوی AcademicExceptionHandler: {@code @Order(0)} تا قبل از
 * {@code PlatformExceptionHandler} اجرا شود، و نام Bean از طریق
 * {@code @Component("...")} تنظیم شده نه {@code @RestControllerAdvice("...")}
 * (که value آن به basePackages نگاشت می‌شود و handler را بی‌اثر می‌کند -
 * رجوع به توضیح مفصل در AcademicExceptionHandler).
 */
@RestControllerAdvice
@Component("financeExceptionHandler")
@Order(0)
public class FinanceExceptionHandler {

    @ExceptionHandler(FinanceException.class)
    public ProblemDetail handleFinanceException(FinanceException ex) {
        return switch (ex.error()) {
            case FinanceError.TuitionFeeNotSet e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "tuition-fee-not-set",
                    "شهریه تنظیم نشده است",
                    "برای مدرسه‌ی با شناسه " + e.schoolId() + " هنوز شهریه‌ای تنظیم نشده است."
            );
            case FinanceError.InvalidTuitionFee e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-tuition-fee",
                    "مبلغ شهریه نامعتبر است",
                    e.reason()
            );
        };
    }
}
