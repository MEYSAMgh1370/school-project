package com.example.roshdeandishe.finance.shared;

import org.springframework.core.annotation.Order;
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
 * <p>
 * فقط مسئول routing است: نگاشت خطا → ProblemDetail در
 * {@link FinanceProblemResolver} زندگی می‌کند (الگوی IamExceptionHandler/
 * IamProblemResolver - وقتی هزینه‌ها اضافه شدند تعداد caseها از حد
 * switch-داخل-handler گذشت، CLAUDE.md §6).
 */
@RestControllerAdvice
@Component("financeExceptionHandler")
@Order(0)
public class FinanceExceptionHandler {

    private final FinanceProblemResolver resolver;

    public FinanceExceptionHandler(FinanceProblemResolver resolver) {
        this.resolver = resolver;
    }

    @ExceptionHandler(FinanceException.class)
    public ProblemDetail handleFinanceException(FinanceException ex) {
        return resolver.resolve(ex.error());
    }
}
