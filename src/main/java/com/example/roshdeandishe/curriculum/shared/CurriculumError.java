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
}
