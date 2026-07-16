package com.example.roshdeandishe.studyplan.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * تخصیص برنامه به دانش‌آموز - رفرنس مات *داخل* studyplan به‌جای ستون
 * planId روی StudentEntity ماژول academic (MOSHAVERE_PORT_SPEC.md §6):
 * academic نباید بداند studyplan وجود دارد. {@code studentId} یکتاست -
 * هر دانش‌آموز در هر لحظه دقیقاً یک برنامه (همان FK تکی Student.plan
 * موshavere)؛ تخصیص جدید upsert است.
 */
@Entity
@Table(name = "student_plan_assignments", schema = "studyplan",
        uniqueConstraints = @UniqueConstraint(columnNames = {"student_id"}))
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

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    StudentPlanAssignmentEntity(Long schoolId, Long studentId, Long planId, Instant assignedAt) {
        this.schoolId = schoolId;
        this.studentId = studentId;
        this.planId = planId;
        this.assignedAt = assignedAt;
    }
}
