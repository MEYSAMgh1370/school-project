package com.example.roshdeandishe.exam.shared;

public sealed interface ExamError {

    /**
     * آزمون یافت نشد - «آزمونِ متعلق به مدرسه‌ی دیگر» هم همین خطاست
     * (همان قرارداد PlanNotFound/TopicNotFound).
     */
    record ExamNotFound(Long examId) implements ExamError {
    }

    record SectionNotFound(Long sectionId) implements ExamError {
    }

    /**
     * سرفصل در این مدرسه یافت نشد - topicId مات به curriculum اشاره دارد،
     * از طریق CurriculumFacade.listTopics اعتبارسنجی می‌شود.
     */
    record TopicNotFound(Long topicId) implements ExamError {
    }

    /**
     * سؤال در بانک سؤال این مدرسه یافت نشد - از طریق CurriculumFacade
     * اعتبارسنجی می‌شود.
     */
    record QuestionNotFound(Long questionId) implements ExamError {
    }

    /**
     * دانش‌آموز در این مدرسه یافت نشد - از طریق AcademicFacade.findStudentInfo
     * اعتبارسنجی می‌شود.
     */
    record StudentNotFound(Long studentId) implements ExamError {
    }

    record InvalidExam(String detail) implements ExamError {
    }

    /**
     * برای این آزمون قبلاً بخشی روی همین سرفصل ساخته شده - همان
     * unique_together (exam, topic) منبع.
     */
    record DuplicateSection(Long examId, Long topicId) implements ExamError {
    }

    /**
     * ساخت خودکار بخش با lazy=false و سرفصلی که به‌اندازه‌ی کافی سؤال ندارد -
     * پورت مستقیم خطای pick_random_questions موshavere.
     */
    record InsufficientQuestions(Long topicId, int requested, int available) implements ExamError {
    }

    /**
     * دانش‌آموز مجاز به شروع تلاش برای این آزمون نیست - آزمون هنوز شروع
     * نشده/به پایان رسیده، یا دانش‌آموز مالک/تخصیص‌یافته‌ی این آزمون نیست
     * (پورت can_attempt موshavere).
     */
    record CannotAttempt(String detail) implements ExamError {
    }

    /**
     * دانش‌آموز قبلاً یک تلاش برای این آزمون داشته و آن تلاش منقضی شده -
     * «فقط آزمون تک‌تلاشی پشتیبانی می‌شود» (کامنت صریح Attempt موshavere)؛
     * توکن تازه صادر نمی‌شود، این تنها فرصت بود.
     */
    record AttemptExpired(Long examId, Long studentId) implements ExamError {
    }

    /**
     * توکن جلسه‌ی آزمون نامعتبر/منقضی/بدون امضای درست است - مکانیزم مجوز
     * مستقل ExamTakeController (MOSHAVERE_PORT_SPEC.md §5)، نه
     * PermissionDenied معمول.
     */
    record InvalidExamToken(String detail) implements ExamError {
    }

    /**
     * نتیجه‌ی آزمون فقط بعد از پایان زمان آزمون قابل مشاهده است - پورت
     * ExamHasEnded موshavere.
     */
    record ExamNotEnded(Long examId) implements ExamError {
    }

    /**
     * دانش‌آموز برای این آزمون هیچ تلاشی ثبت نکرده - پورت
     * StudentAttemptedExam موshavere.
     */
    record NoAttempt(Long examId, Long studentId) implements ExamError {
    }

    record InvalidSubmission(String detail) implements ExamError {
    }
}
