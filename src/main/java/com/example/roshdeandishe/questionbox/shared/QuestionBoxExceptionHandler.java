package com.example.roshdeandishe.questionbox.shared;

import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * همان الگوی FinanceExceptionHandler: {@code @Order(0)} تا قبل از {@code PlatformExceptionHandler}
 * اجرا شود، و نام Bean صریح و یکتا از طریق {@code @Component("...")} است (نه
 * {@code @RestControllerAdvice("...")} که value آن به basePackages نگاشت می‌شود و handler را بی‌اثر می‌کند).
 * فقط routing است؛ نگاشت در {@link QuestionBoxProblemResolver}.
 */
@RestControllerAdvice
@Component("questionboxExceptionHandler")
@Order(0)
public class QuestionBoxExceptionHandler {

    private final QuestionBoxProblemResolver resolver;

    public QuestionBoxExceptionHandler(QuestionBoxProblemResolver resolver) {
        this.resolver = resolver;
    }

    @ExceptionHandler(QuestionBoxException.class)
    public ProblemDetail handleQuestionBoxException(QuestionBoxException ex) {
        return resolver.resolve(ex.error());
    }
}
