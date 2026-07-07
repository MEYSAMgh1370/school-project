package com.example.roshdeandishe.academic.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * پیوند «این کاربر، ولیِ این دانش‌آموز است». برخلاف StudentEntity.userId
 * (که هویت خودِ دانش‌آموز است)، guardianUserId هویت یک User متفاوت است -
 * معمولاً کسی با نقش PARENT در همین مدرسه، اما این Entity آن را اجرا
 * نمی‌کند؛ اعتبارسنجی (آیا این کاربر واقعاً PARENT فعال در این مدرسه است)
 * در AcademicFacadeImpl.linkGuardian انجام می‌شود، جایی که IamFacade در
 * دسترس است - همان‌طور که enrollStudent هم اعتبارسنجی‌های iam را در لایه‌ی
 * Facade انجام می‌دهد، نه در خودِ Entity.
 * <p>
 * studentId یک FK واقعی به StudentEntity همین ماژول است (بر خلاف
 * guardianUserId که - طبق قرارداد قبلی این ماژول - فقط یک شناسه‌ی عددی
 * مات از دید academic است، دقیقاً مثل StudentEntity.userId).
 */
@Entity
@Table(name = "student_guardians", schema = "academic",
        uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "guardian_user_id"}))
@Getter
@NoArgsConstructor
class StudentGuardianEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "guardian_user_id", nullable = false)
    private Long guardianUserId;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    StudentGuardianEntity(Long schoolId, Long studentId, Long guardianUserId, Instant linkedAt) {
        this.schoolId = schoolId;
        this.studentId = studentId;
        this.guardianUserId = guardianUserId;
        this.linkedAt = linkedAt;
    }
}
