package com.example.roshdeandishe.studyplan.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * یک برنامه‌ی مطالعاتی مدرسه - پورت مدل Plan از plan/models.py در
 * moshavere. {@code teacherId} یک Long غیر-FK به کاربر iam است، همان
 * قرارداد ClassEntity.teacherId (بدون FK واقعی چون academic/studyplan از
 * iam فقط شناسه‌ی مبهم می‌خواهند، نه رابطه‌ی دیتابیسی).
 * <p>
 * {@code courseInfoJson} پورتِ course_info در منبع است (JSONField با شمای
 * {course, weight, priority} - COURSE_INFO_SCHEMA در account/constants.py).
 * به‌جای ستون jsonb، عمداً یک ستون متنی ساده (columnDefinition="text")
 * است تا از ناسازگاری نوع JDBC هنگام نوشتن String در ستون jsonb (بدون
 * FormatMapper اختصاصی هایبرنیت) پرهیز شود - (де)serialize کردن به لیست
 * تایپ‌شده‌ی {@link com.example.roshdeandishe.studyplan.StudyPlanFacade.CourseWeight}
 * کاملاً در FacadeImpl انجام می‌شود، نه اینجا.
 */
@Entity
@Table(name = "plans", schema = "studyplan")
@Getter
@Setter
@NoArgsConstructor
class PlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "course_info", nullable = false, columnDefinition = "text")
    private String courseInfoJson;

    PlanEntity(Long schoolId, Long teacherId, String title, String courseInfoJson) {
        this.schoolId = schoolId;
        this.teacherId = teacherId;
        this.title = title;
        this.courseInfoJson = courseInfoJson;
    }
}
