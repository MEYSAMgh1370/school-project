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

    record InvalidTopic(String reason) implements CurriculumError {
    }

    record InvalidQuestion(String reason) implements CurriculumError {
    }
}
