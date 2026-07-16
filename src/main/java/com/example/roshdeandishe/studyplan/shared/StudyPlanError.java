package com.example.roshdeandishe.studyplan.shared;

import java.time.LocalDate;

/**
 * مدل خطای دامنه‌محور مختص ماژول studyplan.
 */
public sealed interface StudyPlanError {

    /**
     * برنامه یافت نشد - «برنامه‌ی متعلق به مدرسه‌ی دیگر» هم همین خطاست
     * (همان قرارداد ClassNotFound/TopicNotFound).
     */
    record PlanNotFound(Long planId) implements StudyPlanError {
    }

    /**
     * دانش‌آموز در این مدرسه یافت نشد. studentId شناسه‌ی StudentEntity
     * ماژول academic است (Long مات از دید این ماژول) - اعتبارسنجی از طریق
     * AcademicFacade.findStudentInfo انجام می‌شود.
     */
    record StudentNotFound(Long studentId) implements StudyPlanError {
    }

    /**
     * سرفصل در این مدرسه یافت نشد - topicId مات به curriculum اشاره دارد و
     * از طریق CurriculumFacade.listTopics اعتبارسنجی می‌شود.
     */
    record TopicNotFound(Long topicId) implements StudyPlanError {
    }

    record InvalidPlan(String detail) implements StudyPlanError {
    }

    record InvalidWeekCourse(String detail) implements StudyPlanError {
    }

    /**
     * (plan, weekStart, courseTopic) باید یکتا باشد - همان unique_together
     * موshavere.
     */
    record DuplicateWeekCourse(Long planId, LocalDate weekStart, Long courseTopicId) implements StudyPlanError {
    }

    record InvalidSchedule(String detail) implements StudyPlanError {
    }

    /**
     * شروع هفته باید شنبه باشد (هفته‌ی درسی ایران) - همان چک
     * weekday()==Saturday در moshavere و قید DB روی WeekCourse.date.
     */
    record InvalidWeekStart(String detail) implements StudyPlanError {
    }

    /**
     * پیشنهاد خودکار فقط برای هفته‌ی خالی مجاز است - وگرنه بازه‌های موجود
     * با پیشنهادی‌ها تداخل می‌کردند («This week is not empty!» moshavere).
     */
    record WeekNotEmpty(LocalDate weekStart) implements StudyPlanError {
    }

    /**
     * مجموع زمان آزاد برنامه‌ی هفتگی کمتر از ۱۸۰ دقیقه است - پورت مستقیم
     * خطای FillSlices در moshavere (کمتر از این مقدار، توزیع وزنی بین
     * درس‌ها بی‌معنا می‌شود).
     */
    record ScheduleTooSmall(int totalMinutes) implements StudyPlanError {
    }

    /**
     * دانش‌آموز هنوز به هیچ برنامه‌ای تخصیص داده نشده - پیشنهاد خودکار بدون
     * وزن‌دهی درس‌های یک برنامه ممکن نیست.
     */
    record PlanNotAssigned(Long studentId) implements StudyPlanError {
    }

    record IntervalNotFound(Long intervalId) implements StudyPlanError {
    }

    /**
     * بازه‌ی جدید با بازه‌ی مطالعه‌ی موجودِ همین دانش‌آموز هم‌پوشانی دارد -
     * پورت check_time_overlap در moshavere.
     */
    record IntervalOverlaps(String detail) implements StudyPlanError {
    }

    record InvalidInterval(String detail) implements StudyPlanError {
    }
}
