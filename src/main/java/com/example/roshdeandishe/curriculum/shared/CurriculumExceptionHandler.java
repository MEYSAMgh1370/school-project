package com.example.roshdeandishe.curriculum.shared;

import com.example.roshdeandishe.platform.ProblemDetailFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
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
 * AcademicExceptionHandler، همین دام یک بار واقعاً رخ داده).
 */
@RestControllerAdvice
@Component("curriculumExceptionHandler")
@Order(0)
public class CurriculumExceptionHandler {

    @ExceptionHandler(CurriculumException.class)
    public ProblemDetail handleCurriculumException(CurriculumException ex) {
        return switch (ex.error()) {
            case CurriculumError.TopicNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "topic-not-found",
                    "سرفصل یافت نشد",
                    "سرفصلی با شناسه " + e.topicId() + " یافت نشد."
            );
            case CurriculumError.InvalidTopic e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-topic",
                    "سرفصل نامعتبر",
                    e.detail()
            );
            case CurriculumError.InvalidQuestion e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-question",
                    "سوال نامعتبر",
                    e.detail()
            );
        };
    }
}
