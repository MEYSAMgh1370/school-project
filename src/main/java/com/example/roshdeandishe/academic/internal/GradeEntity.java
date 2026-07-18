package com.example.roshdeandishe.academic.internal;

import com.example.roshdeandishe.academic.DescriptiveGradeLevel;
import com.example.roshdeandishe.academic.GradingScheme;
import com.example.roshdeandishe.academic.GradingTerm;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * نمره‌ی یک دانش‌آموز در یک کلاس برای یک نوبتِ مشخص. {@code scheme} در لحظه‌ی
 * ثبت از خودِ ClassEntity کپی می‌شود (نه فقط ارجاع داده شود) - همان قرارداد
 * InvoiceEntity.amount که از TuitionFee در لحظه‌ی صدور کپی می‌شود - تا تغییر
 * بعدیِ شِمای یک کلاس، نمره‌های قبلاً ثبت‌شده را دگرگون نکند.
 * <p>
 * دقیقاً یکی از {@code descriptiveLevel}/{@code numericScore} پر است، بسته
 * به {@code scheme} - اعتبارسنجی در AcademicFacadeImpl.recordGrade انجام
 * می‌شود، نه در خودِ Entity.
 */
@Entity
@Table(name = "grades", schema = "academic",
        uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "class_id", "academic_year", "term"}))
@Getter
@Setter
@NoArgsConstructor
class GradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "academic_year", nullable = false, length = 16)
    private String academicYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GradingTerm term;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GradingScheme scheme;

    @Enumerated(EnumType.STRING)
    @Column(name = "descriptive_level", length = 32)
    private DescriptiveGradeLevel descriptiveLevel;

    @Column(name = "numeric_score", precision = 4, scale = 2)
    private BigDecimal numericScore;

    @Column(length = 1000)
    private String comment;

    @Column(name = "recorded_by_user_id")
    private Long recordedByUserId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
}
