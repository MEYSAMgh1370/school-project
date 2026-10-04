package com.example.roshdeandishe.academic.internal;

import com.example.roshdeandishe.academic.PromotionOutcome;
import com.example.roshdeandishe.academic.StudentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** وضعیتِ هر دانش‌آموز پیش از یک اجرای ارتقا و نتیجه‌ای که گرفت - مبنای برگرداندن (undo). */
@Entity
@Table(name = "promotion_run_students", schema = "academic")
@Getter
@Setter
@NoArgsConstructor
class PromotionRunStudentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private PromotionOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 16)
    private StudentStatus previousStatus;

    @Column(name = "previous_class_id")
    private Long previousClassId;

    @Column(name = "membership_deactivated", nullable = false)
    private boolean membershipDeactivated;

    PromotionRunStudentEntity(Long runId, Long studentId, PromotionOutcome outcome, StudentStatus previousStatus,
                              Long previousClassId, boolean membershipDeactivated) {
        this.runId = runId;
        this.studentId = studentId;
        this.outcome = outcome;
        this.previousStatus = previousStatus;
        this.previousClassId = previousClassId;
        this.membershipDeactivated = membershipDeactivated;
    }
}
