package com.example.roshdeandishe.studyplan.shared;

import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * {@code @Order(0)}: باید قبل از {@code PlatformExceptionHandler}
 * ({@code @Order(Integer.MAX_VALUE)}) اجرا شود.
 * <p>
 * ⚠️ نام Bean از طریق {@code @Component("...")} تنظیم شده، نه
 * {@code @RestControllerAdvice("...")} - {@code value()} در
 * {@code @ControllerAdvice} به {@code basePackages} نگاشت می‌شود و پاس دادن
 * رشته به آن، این handler را برای همیشه غیرقابل‌اعمال می‌کند (رجوع کنید به
 * CurriculumExceptionHandler، همین دام یک بار واقعاً رخ داده).
 */
@RestControllerAdvice
@Component("studyPlanExceptionHandler")
@Order(0)
public class StudyPlanExceptionHandler {

    private final StudyPlanProblemResolver problemResolver;

    public StudyPlanExceptionHandler(StudyPlanProblemResolver problemResolver) {
        this.problemResolver = problemResolver;
    }

    @ExceptionHandler(StudyPlanException.class)
    public ProblemDetail handleStudyPlanException(StudyPlanException ex) {
        return problemResolver.resolve(ex.error());
    }
}
