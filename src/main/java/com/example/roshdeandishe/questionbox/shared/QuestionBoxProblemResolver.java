package com.example.roshdeandishe.questionbox.shared;

import com.example.roshdeandishe.platform.ProblemDetailFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * نگاشت QuestionBoxError به ProblemDetail، جدا از handler تا بدون MVC قابل unit-test باشد
 * (الگوی AttendanceProblemResolver). پسوندهای type kebab-case‌اند.
 */
@Component
public class QuestionBoxProblemResolver {

    public ProblemDetail resolve(QuestionBoxError error) {
        return switch (error) {
            case QuestionBoxError.QuestionNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "question-not-found", "سؤال یافت نشد",
                    "سؤالی با شناسه " + e.questionId() + " یافت نشد.");
            case QuestionBoxError.SheetNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "sheet-not-found", "برگه یافت نشد",
                    "برگه‌ای با شناسه " + e.sheetId() + " یافت نشد.");
            case QuestionBoxError.HeaderNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "header-not-found", "سربرگ یافت نشد",
                    "سربرگی با شناسه " + e.headerId() + " یافت نشد.");
            case QuestionBoxError.ImageNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "image-not-found", "تصویر یافت نشد",
                    "تصویری با شناسه " + e.imageId() + " یافت نشد.");
            case QuestionBoxError.InvalidQuestion e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-question", "سؤال نامعتبر است", e.reason());
            case QuestionBoxError.InvalidImage e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-image", "تصویر نامعتبر است", e.reason());
            case QuestionBoxError.InvalidSheet e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-sheet", "برگه نامعتبر است", e.reason());
            case QuestionBoxError.NotAllowed e -> ProblemDetailFactory.problem(
                    HttpStatus.FORBIDDEN, "question-box-not-allowed", "اجازه‌ی تغییر ندارید",
                    "فقط سازنده یا مدیر مدرسه می‌تواند این مورد را تغییر دهد.");
        };
    }
}
