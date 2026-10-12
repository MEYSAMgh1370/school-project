package com.example.roshdeandishe.curriculum.shared;

import com.example.roshdeandishe.platform.ProblemDetailFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * همان الگوی AcademicExceptionHandler: {@code @Order(0)} تا قبل از
 * PlatformExceptionHandler اجرا شود، و نام Bean از طریق
 * {@code @Component("...")} - نه {@code @RestControllerAdvice("...")} که
 * value آن به basePackages نگاشت می‌شود و handler را غیرقابل‌اعمال می‌کند
 * (توضیح کامل در AcademicExceptionHandler).
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
                    "سرفصلی با شناسه " + e.topicId() + " در این مدرسه یافت نشد."
            );
            case CurriculumError.ParentTopicNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "parent-topic-not-found",
                    "سرفصل والد یافت نشد",
                    "سرفصل پلتفرمی با کد منبع «" + e.parentSourceCode() + "» یافت نشد."
            );
            case CurriculumError.InvalidTopic e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-topic",
                    "سرفصل نامعتبر",
                    e.reason()
            );
            case CurriculumError.InvalidQuestion e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-question",
                    "سؤال نامعتبر",
                    e.reason()
            );
            case CurriculumError.QuestionNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "question-not-found",
                    "سؤال یافت نشد",
                    "سؤالی با شناسه " + e.questionId() + " برای این مدرسه یافت نشد."
            );
            case CurriculumError.ReportNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "question-report-not-found",
                    "گزارش یافت نشد",
                    "گزارشی با شناسه " + e.reportId() + " یافت نشد."
            );
            case CurriculumError.InvalidReport e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-question-report",
                    "گزارش نامعتبر",
                    e.reason()
            );
            case CurriculumError.InvalidReportTransition e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "invalid-report-transition",
                    "تغییر وضعیت گزارش مجاز نیست",
                    "گزارش از وضعیت " + e.from() + " به " + e.to() + " نمی‌رود."
            );
            case CurriculumError.InvalidMoshavereBundle e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "moshavere-bundle-invalid",
                    "بسته‌ی مشاوره نامعتبر",
                    e.reason()
            );
        };
    }
}
