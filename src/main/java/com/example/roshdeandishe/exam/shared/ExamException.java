package com.example.roshdeandishe.exam.shared;

public final class ExamException extends RuntimeException {

    private final ExamError error;

    public ExamException(ExamError error) {
        super(describe(error));
        this.error = error;
    }

    public ExamError error() {
        return error;
    }

    private static String describe(ExamError error) {
        return switch (error) {
            case ExamError.ExamNotFound e -> "آزمونی با شناسه " + e.examId() + " در این مدرسه یافت نشد.";
            case ExamError.SectionNotFound e -> "بخشی با شناسه " + e.sectionId() + " یافت نشد.";
            case ExamError.TopicNotFound e -> "سرفصلی با شناسه " + e.topicId() + " در این مدرسه یافت نشد.";
            case ExamError.QuestionNotFound e -> "سؤالی با شناسه " + e.questionId() + " یافت نشد.";
            case ExamError.StudentNotFound e -> "دانش‌آموزی با شناسه " + e.studentId() + " در این مدرسه یافت نشد.";
            case ExamError.InvalidExam e -> e.detail();
            case ExamError.DuplicateSection e ->
                    "برای آزمون " + e.examId() + " روی سرفصل " + e.topicId() + " قبلاً بخشی ساخته شده است.";
            case ExamError.InsufficientQuestions e ->
                    "سرفصل " + e.topicId() + " فقط " + e.available() + " سؤال دارد، " + e.requested() + " سؤال لازم بود.";
            case ExamError.CannotAttempt e -> e.detail();
            case ExamError.AttemptExpired e ->
                    "تلاش دانش‌آموز " + e.studentId() + " برای آزمون " + e.examId() + " منقضی شده است.";
            case ExamError.InvalidExamToken e -> e.detail();
            case ExamError.ExamNotEnded e -> "آزمون " + e.examId() + " هنوز به پایان نرسیده است.";
            case ExamError.NoAttempt e -> "دانش‌آموز " + e.studentId() + " برای آزمون " + e.examId() + " تلاشی ثبت نکرده است.";
            case ExamError.InvalidSubmission e -> e.detail();
        };
    }
}
