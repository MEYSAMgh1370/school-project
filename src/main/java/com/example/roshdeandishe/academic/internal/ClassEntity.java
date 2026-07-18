package com.example.roshdeandishe.academic.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * یک کلاس/بخش درسی در یک مدرسه (مثلاً "پایه پنجم - الف"). {@code teacherId}
 * به userId کاربری در ماژول iam اشاره دارد که عضویت TEACHER در همین مدرسه
 * دارد - این ارتباط در سطح دیتابیس enforce نمی‌شود (academic هیچ FK ای به
 * جداول iam ندارد، طبق مرز ماژولی)، فقط یک قرارداد منطقی است.
 * <p>
 * {@code teacherId} عمداً nullable است: یک کلاس ممکن است قبل از تخصیص
 * معلم ساخته شود (مثلاً در ابتدای سال تحصیلی، چیدمان کلاس‌ها قبل از
 * استخدام/تخصیص معلم انجام می‌شود).
 */
@Entity
@Table(name = "classes", schema = "academic")
@Getter
@Setter
@NoArgsConstructor
class ClassEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "teacher_id")
    private Long teacherId;

    @Column(length = 32)
    private String section;

    @Column(name = "grade_level", nullable = false)
    private int gradeLevel;

    ClassEntity(Long schoolId, String name, Long teacherId, String section, int gradeLevel) {
        this.schoolId = schoolId;
        this.name = name;
        this.teacherId = teacherId;
        this.section = section;
        this.gradeLevel = gradeLevel;
    }
}
