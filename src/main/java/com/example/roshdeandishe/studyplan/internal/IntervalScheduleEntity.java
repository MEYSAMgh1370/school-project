package com.example.roshdeandishe.studyplan.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * پورت مدل IntervalSchedule از study_table/models.py: قالب هفتگیِ زمان
 * آزادِ یک دانش‌آموز برای مطالعه. {@code defaultScheduleJson}/
 * {@code currentScheduleJson} پورتِ default_schedule/current_schedule
 * (هر دو JSONField در منبع) - همان دلیل ستون متنی که در PlanEntity
 * توضیح داده شد (نه jsonb، برای پرهیز از ناسازگاری نوع JDBC بدون
 * FormatMapper اختصاصی).
 * <p>
 * منبع تازگیِ برنامه‌ی «این هفته ویرایش شده» را با مقایسه‌ی روزهای هفته‌ی
 * شمسی محاسبه می‌کرد (schedule property در IntervalSchedule)؛ اینجا
 * عمداً با یک مقایسه‌ی timestamp ساده (آخرین ویرایش در ۷ روز گذشته بوده؟)
 * جایگزین شده - تقویم شمسی در جای دیگری از این پروژه فقط برای نمایش
 * بازه‌ی گزارش هزینه به‌کار رفته (jalali.js)، نه به‌عنوان یک قاعده‌ی
 * دامنه‌ی عمومی - MOSHAVERE_PORT_SPEC.md §6.
 */
@Entity
@Table(name = "interval_schedules", schema = "studyplan")
@Getter
@Setter
@NoArgsConstructor
class IntervalScheduleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "default_schedule", nullable = false, columnDefinition = "text")
    private String defaultScheduleJson;

    @Column(name = "current_schedule", columnDefinition = "text")
    private String currentScheduleJson;

    @Column(name = "last_edited_at")
    private LocalDateTime lastEditedAt;

    IntervalScheduleEntity(Long schoolId, Long studentId, String defaultScheduleJson) {
        this.schoolId = schoolId;
        this.studentId = studentId;
        this.defaultScheduleJson = defaultScheduleJson;
    }
}
