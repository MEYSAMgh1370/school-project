package com.example.roshdeandishe.finance.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * یک ردیف audit یعنی: "چه کسی، چه اقدام مالی‌ای را، چه زمانی انجام داد".
 * فقط append است - هیچ متد update/delete روی این Entity در نظر گرفته نشده،
 * چون اصل audit trail این است که تغییرناپذیر (immutable) بماند.
 */
@Entity
@Table(name = "finance_audit", schema = "finance")
@Getter
@Setter
@NoArgsConstructor
class FinanceAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "student_id")
    private Long studentId;

    /**
     * فقط برای اقدام‌های EXPENSE_* پر می‌شود - nullable مثل studentId (که
     * فقط برای اقدام‌های فاکتور/پرداخت پر است): یک جدول audit واحد برای همه‌ی
     * اقدام‌های مالی مدرسه، نه دو timeline نصفه.
     */
    @Column(name = "expense_id")
    private Long expenseId;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    FinanceAuditEntity(Long schoolId, Long actorUserId, String action, Long studentId, BigDecimal amount, Instant occurredAt) {
        this.schoolId = schoolId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.studentId = studentId;
        this.amount = amount;
        this.occurredAt = occurredAt;
    }

    static FinanceAuditEntity forExpense(Long schoolId, Long actorUserId, String action,
                                         Long expenseId, BigDecimal amount, Instant occurredAt) {
        FinanceAuditEntity entity = new FinanceAuditEntity(schoolId, actorUserId, action, null, amount, occurredAt);
        entity.expenseId = expenseId;
        return entity;
    }
}
