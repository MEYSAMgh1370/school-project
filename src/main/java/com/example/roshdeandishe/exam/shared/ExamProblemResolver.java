package com.example.roshdeandishe.exam.shared;

import com.example.roshdeandishe.platform.ProblemDetailFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * نگاشت ExamError به ProblemDetail - ۱۳ case از حد «چند مورد ساده»ی
 * CLAUDE.md §6 می‌گذرد، پس مثل Iam/Finance/StudyPlanProblemResolver در یک
 * @Component مستقلِ قابل unit-test بدون MVC جدا شده.
 */
@Component
public class ExamProblemResolver {

    public ProblemDetail resolve(ExamError error) {
        return switch (error) {
            case ExamError.ExamNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "exam-not-found",
                    "آزمون یافت نشد",
                    "آزمونی با شناسه " + e.examId() + " در این مدرسه وجود ندارد."
            );
            case ExamError.SectionNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "section-not-found",
                    "بخش یافت نشد",
                    "بخشی با شناسه " + e.sectionId() + " در این آزمون وجود ندارد."
            );
            case ExamError.TopicNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "topic-not-found",
                    "سرفصل یافت نشد",
                    "سرفصلی با شناسه " + e.topicId() + " در این مدرسه وجود ندارد."
            );
            case ExamError.QuestionNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "question-not-found",
                    "سؤال یافت نشد",
                    "سؤالی با شناسه " + e.questionId() + " یافت نشد."
            );
            case ExamError.StudentNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "student-not-found",
                    "دانش‌آموز یافت نشد",
                    "دانش‌آموزی با شناسه " + e.studentId() + " در این مدرسه وجود ندارد."
            );
            case ExamError.InvalidExam e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-exam",
                    "آزمون نامعتبر",
                    e.detail()
            );
            case ExamError.DuplicateSection e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "duplicate-section",
                    "بخش تکراری",
                    "برای آزمون " + e.examId() + " روی سرفصل " + e.topicId() + " قبلاً بخشی ساخته شده است."
            );
            case ExamError.InsufficientQuestions e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "insufficient-questions",
                    "سؤال کافی نیست",
                    "سرفصل " + e.topicId() + " فقط " + e.available() + " سؤال دارد، " + e.requested() + " سؤال لازم بود."
            );
            case ExamError.CannotAttempt e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "cannot-attempt",
                    "شروع تلاش ممکن نیست",
                    e.detail()
            );
            case ExamError.AttemptExpired e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "attempt-expired",
                    "تلاش منقضی شده",
                    "تلاش دانش‌آموز " + e.studentId() + " برای آزمون " + e.examId()
                            + " منقضی شده است - این آزمون تک‌تلاشی است."
            );
            case ExamError.InvalidExamToken e -> ProblemDetailFactory.problem(
                    HttpStatus.UNAUTHORIZED, "invalid-exam-token",
                    "توکن جلسه‌ی آزمون نامعتبر",
                    e.detail()
            );
            case ExamError.ExamNotEnded e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "exam-not-ended",
                    "آزمون هنوز به پایان نرسیده",
                    "نتیجه‌ی آزمون " + e.examId() + " فقط بعد از پایان زمان آزمون قابل مشاهده است."
            );
            case ExamError.NoAttempt e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "no-attempt",
                    "تلاشی ثبت نشده",
                    "دانش‌آموز " + e.studentId() + " برای آزمون " + e.examId() + " تلاشی ثبت نکرده است."
            );
            case ExamError.InvalidSubmission e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-submission",
                    "پاسخ نامعتبر",
                    e.detail()
            );
        };
    }
}
