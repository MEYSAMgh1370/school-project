package com.example.roshdeandishe.studyplan.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * لینک دانش‌آموز <-> برنامه‌ی مطالعاتی، به‌عنوان یک ارجاع مبهم درونِ
 * studyplan نگه‌داشته شده (بدون FK به academic) - همان قراردادِ
 * StudentGuardianEntity.guardianUserId/ClassEntity.teacherId - تا academic
 * لازم نباشد از وجود studyplan خبر داشته باشد (MOSHAVERE_PORT_SPEC.md §6).
 * برخلاف منبع (Student.plan یک FK ساده)، اینجا یک جدول جدا با
 * unique(schoolId, studentId) است: هر دانش‌آموز حداکثر یک برنامه‌ی فعال
 * در هر مدرسه دارد؛ تخصیص دوباره رکورد موجود را جایگزین می‌کند
 * (idempotent-به-معنای-جایگزینی، همان الگوی enrollStudent در academic).
 */
@Entity
@Table(
        name = "student_plan_assignments",
        schema = "studyplan",
        uniqueConstraints = @UniqueConstraint(columnNames = {"school_id", "student_id"})
)
@Getter
@Setter
@NoArgsConstructor
class StudentPlanAssignmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    StudentPlanAssignmentEntity(Long schoolId, Long studentId, Long planId) {
        this.schoolId = schoolId;
        this.studentId = studentId;
        this.planId = planId;
    }
}
