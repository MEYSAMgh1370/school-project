package com.example.roshdeandishe.studyplan.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * برنامه‌ی زمانی هفتگی زمان‌های آزاد یک دانش‌آموز. دو ستون
 * default/current طبق MOSHAVERE_PORT_SPEC.md §6 jsonb می‌مانند (ساختار
 * تو در توی روز→بازه‌ها؛ serialization/اعتبارسنجی در facade با Jackson) -
 * برخلاف وزن‌دهی برنامه که جدول فرزند شد. {@code lastEditedAt} جایگزین
 * چک «weekday تقویم پارسی» موshavere با مقایسه‌ی timestamp ساده است:
 * currentSchedule فقط وقتی مؤثر است که از شنبه‌ی اخیر به بعد ویرایش شده
 * باشد.
 */
@Entity
@Table(name = "interval_schedules", schema = "studyplan",
        uniqueConstraints = @UniqueConstraint(columnNames = {"student_id"}))
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_schedule", nullable = false)
    private String defaultSchedule;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "current_schedule")
    private String currentSchedule;

    @Column(name = "last_edited_at")
    private Instant lastEditedAt;

    IntervalScheduleEntity(Long schoolId, Long studentId, String defaultSchedule) {
        this.schoolId = schoolId;
        this.studentId = studentId;
        this.defaultSchedule = defaultSchedule;
    }
}
