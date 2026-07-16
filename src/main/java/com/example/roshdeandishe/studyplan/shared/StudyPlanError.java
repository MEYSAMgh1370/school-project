package com.example.roshdeandishe.studyplan.shared;

import java.time.LocalDate;

/**
 * مدل خطای دامنه‌محور مختص ماژول studyplan.
 */
public sealed interface StudyPlanError {

    record PlanNotFound(Long planId) implements StudyPlanError {
    }

    record InvalidPlan(String reason) implements StudyPlanError {
    }

    /**
     * سرفصلی با این شناسه در این مدرسه یافت نشد - سرفصلِ مدرسه‌ی دیگر هم
     * عمداً همین خطاست، همان قرارداد TopicNotFound در curriculum.
     */
    record TopicNotFound(Long topicId) implements StudyPlanError {
    }

    record StudentNotFound(Long studentId) implements StudyPlanError {
    }

    /**
     * دانش‌آموز هیچ برنامه‌ی مطالعاتی‌ای در این مدرسه ندارد - پیشنهاد
     * خودکار بدون course_info امکان‌پذیر نیست.
     */
    record PlanNotAssigned(Long studentId) implements StudyPlanError {
    }

    record InvalidWeekCourse(String reason) implements StudyPlanError {
    }

    /**
     * weekStartDate ورودی (برای createWeekCourse یا هر عملیات هفتگیِ
     * دیگر) باید شنبه باشد - پورت CheckConstraint {@code date__week_day=7}
     * در منبع، اینجا به‌عنوان اعتبارسنجی سطح Facade.
     */
    record NotSaturday(LocalDate date) implements StudyPlanError {
    }

    record InvalidSchedule(String reason) implements StudyPlanError {
    }

    /**
     * زمان کل بلوک‌های آزادِ هفته کمتر از ۱۸۰ دقیقه است - پورت مستقیم
     * exception صریح {@code compute_interval_per_topic} در منبع.
     */
    record ScheduleTooSmall(int totalMinutes) implements StudyPlanError {
    }

    record WeekNotEmpty(LocalDate weekStartDate) implements StudyPlanError {
    }

    record StudyIntervalNotFound(Long intervalId) implements StudyPlanError {
    }
}
