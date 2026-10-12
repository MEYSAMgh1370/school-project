package com.example.roshdeandishe.curriculum.shared;

/**
 * مدل خطای دامنه‌محور مختص ماژول curriculum.
 */
public sealed interface CurriculumError {

    /**
     * سرفصلی با این شناسه در این مدرسه وجود ندارد - سرفصلِ مدرسه‌ی دیگر
     * هم عمداً همین خطاست (نه 403)، همان قرارداد requireClassExists در
     * academic: از دید این مدرسه «یافت نشد».
     */
    record TopicNotFound(Long topicId) implements CurriculumError {
    }

    /** والدِ پلتفرمیِ داده‌شده با این کدِ منبع (source_code) وجود ندارد. */
    record ParentTopicNotFound(String parentSourceCode) implements CurriculumError {
    }

    record InvalidTopic(String reason) implements CurriculumError {
    }

    record InvalidQuestion(String reason) implements CurriculumError {
    }

    /** سؤالی با این شناسه برای این مدرسه قابل‌مشاهده نیست (وجود ندارد یا اختصاصیِ مدرسه‌ی دیگر است). */
    record QuestionNotFound(Long questionId) implements CurriculumError {
    }

    /** گزارشی با این شناسه در این صف (مدرسه/پلتفرم) وجود ندارد. */
    record ReportNotFound(Long reportId) implements CurriculumError {
    }

    record InvalidReport(String reason) implements CurriculumError {
    }

    /** گردش وضعیت فقط OPEN→REVIEWED، REVIEWED→FIXED و REVIEWED→CLOSED است. */
    record InvalidReportTransition(String from, String to) implements CurriculumError {
    }

    /** بسته‌ی ایمپورت مشاوره نامعتبر است؛ {@code reason} اولین مشکل را نام می‌برد. هیچ چیزی نوشته نشده است. */
    record InvalidMoshavereBundle(String reason) implements CurriculumError {
    }
}
