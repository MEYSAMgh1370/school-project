package com.example.roshdeandishe.academic.internal;

import com.example.roshdeandishe.academic.GradingScheme;
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
 * <p>
 * {@code gradeLevel} (پایه‌ی عددی ۱ تا ۱۲، افزوده‌شده ۲۰۲۶-۰۷-۱۸): در برابر
 * بازه‌ی مقطع تحصیلیِ مدرسه‌ی مالک (school.SchoolFacade.SchoolLevel) در
 * AcademicFacadeImpl.requireValidGradeLevel اعتبارسنجی می‌شود - صرفاً
 * classification، نه چیزی که خودش شِمای نمره‌دهی را تعیین کند.
 * <p>
 * {@code gradingScheme} (CLAUDE.md §11 Q8، حل‌شده): شِمای نمره‌دهی این کلاس
 * (توصیفی/عددی) - در سطح کلاس، نه مدرسه، و به‌صورت صریح روی خودِ کلاس
 * ذخیره می‌شود (نه صرفاً مشتق‌شده در لحظه‌ی خواندن)، تا یک ادمین بتواند آن
 * را به‌ازای هر کلاس override کند. پیش‌فرضش وقتی صریحاً داده نشود از
 * gradeLevel مشتق می‌شود (۱ تا ۶ → DESCRIPTIVE، ۷ تا ۱۲ → NUMERIC) - در
 * CreateClassRequest، نه اینجا. ستون nullable می‌ماند (نه
 * NOT NULL) تا افزودن آن به جدول موجود، دیتابیسِ dev را نشکند.
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

    @Enumerated(EnumType.STRING)
    @Column(name = "grading_scheme", length = 16)
    private GradingScheme gradingScheme;

    ClassEntity(Long schoolId, String name, Long teacherId, String section, int gradeLevel, GradingScheme gradingScheme) {
        this.schoolId = schoolId;
        this.name = name;
        this.teacherId = teacherId;
        this.section = section;
        this.gradeLevel = gradeLevel;
        this.gradingScheme = gradingScheme;
    }
}
