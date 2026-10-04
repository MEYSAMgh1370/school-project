package com.example.roshdeandishe.academic.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * ثبت‌نامِ یک دانش‌آموز در یک سال (academic-year.md فاز ۳): یک ردیف به‌ازای (دانش‌آموز، سال) که تاریخچه را
 * نگه می‌دارد؛ ردیفِ سالِ قبل با سالِ بعد عوض نمی‌شود. {@code classId} تا جا‌دادنِ دانش‌آموز تهی است
 * ({@code PENDING}). {@code StudentEntity.classId} همچنان «ثبت‌نامِ جاری» است که بقیه‌ی ماژول‌ها می‌خوانند.
 */
@Entity
@Table(name = "enrollments", schema = "academic")
@Getter
@Setter
@NoArgsConstructor
class EnrollmentEntity {

    enum Status {
        ENROLLED, PENDING
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "academic_year", nullable = false, length = 16)
    private String academicYear;

    @Column(name = "class_id")
    private Long classId;

    @Column(name = "grade_level", nullable = false)
    private int gradeLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "promotion_run_id")
    private Long promotionRunId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    EnrollmentEntity(Long schoolId, Long studentId, String academicYear, Long classId, int gradeLevel, Status status,
                     Long promotionRunId) {
        this.schoolId = schoolId;
        this.studentId = studentId;
        this.academicYear = academicYear;
        this.classId = classId;
        this.gradeLevel = gradeLevel;
        this.status = status;
        this.promotionRunId = promotionRunId;
        this.createdAt = Instant.now();
    }
}
