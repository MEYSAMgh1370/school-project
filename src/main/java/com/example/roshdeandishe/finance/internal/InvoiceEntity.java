package com.example.roshdeandishe.finance.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * فاکتور شهریه‌ی واقعی و persist شده - جایگزین stub قبلی که فقط event
 * منتشر می‌کرد. audit trail (FinanceAuditEntity) همچنان جدا و append-only
 * می‌ماند: این جدول «فاکتور چیست»، آن جدول «چه کسی چه زمانی چه کرد».
 * <p>
 * عمداً فیلد status/پرداخت ندارد - پرداخت‌ها هنوز ساخته نشده‌اند (CLAUDE.md
 * §1 آن‌ها را در scope ماژول finance می‌داند ولی هنوز خواسته نشده‌اند)؛
 * وقتی پرداخت واقعی اضافه شد، status هم همراه آن می‌آید، نه زودتر.
 */
@Entity
@Table(name = "invoices", schema = "finance")
@Getter
@Setter
@NoArgsConstructor
class InvoiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    InvoiceEntity(Long schoolId, Long studentId, BigDecimal amount, Instant issuedAt) {
        this.schoolId = schoolId;
        this.studentId = studentId;
        this.amount = amount;
        this.issuedAt = issuedAt;
    }
}
