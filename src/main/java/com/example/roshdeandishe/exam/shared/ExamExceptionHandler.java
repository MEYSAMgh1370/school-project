package com.example.roshdeandishe.exam.shared;

import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * {@code @Order(0)}: باید قبل از {@code PlatformExceptionHandler}
 * ({@code @Order(Integer.MAX_VALUE)}) اجرا شود.
 * <p>
 * نام Bean از طریق {@code @Component("...")} تنظیم شده، نه
 * {@code @RestControllerAdvice("...")} - همان دام مستندشده در
 * StudyPlan/CurriculumExceptionHandler.
 */
@RestControllerAdvice
@Component("examExceptionHandler")
@Order(0)
public class ExamExceptionHandler {

    private final ExamProblemResolver problemResolver;

    public ExamExceptionHandler(ExamProblemResolver problemResolver) {
        this.problemResolver = problemResolver;
    }

    @ExceptionHandler(ExamException.class)
    public ProblemDetail handleExamException(ExamException ex) {
        return problemResolver.resolve(ex.error());
    }
}
