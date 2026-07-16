package com.example.roshdeandishe.curriculum.shared;

/**
 * مدل خطای دامنه‌محور مختص ماژول curriculum.
 */
public sealed interface CurriculumError {

    /**
     * سرفصل یافت نشد - از دید این ماژول، «سرفصل متعلق به مدرسه‌ی دیگر»
     * هم همین خطاست (همان قرارداد ClassNotFound در academic).
     */
    record TopicNotFound(Long topicId) implements CurriculumError {
    }

    record InvalidTopic(String detail) implements CurriculumError {
    }

    record InvalidQuestion(String detail) implements CurriculumError {
    }
}
