package com.example.roshdeandishe.academic.internal;

import com.example.roshdeandishe.academic.TimetableDay;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * یک خانه‌ی برنامه: کلاس + زنگ (که سال را دارد) + روز + دقیقاً یکی از
 * {@code courseId}/{@code activityId} + معلمِ اختیاری ({@code teacherUserId}،
 * شناسه‌ی مات کاربر iam).
 */
@Entity
@Table(name = "timetable_entries", schema = "academic")
@Getter
@Setter
@NoArgsConstructor
class TimetableEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "period_id", nullable = false)
    private Long periodId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private TimetableDay weekday;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "activity_id")
    private Long activityId;

    @Column(name = "teacher_user_id")
    private Long teacherUserId;

    TimetableEntryEntity(Long schoolId, Long classId, Long periodId, TimetableDay weekday) {
        this.schoolId = schoolId;
        this.classId = classId;
        this.periodId = periodId;
        this.weekday = weekday;
    }
}
