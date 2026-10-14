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
 * عمداً ستون status ندارد - وضعیت (InvoiceStatus) در لحظه‌ی خواندن از روی
 * (amount, amountPaid, dueDate) مشتق می‌شود، نه persist؛ رجوع به doc خود
 * InvoiceStatus برای دلیل. amountPaid جمعِ نگه‌داشته‌شده‌ی PaymentEntity های
 * این فاکتور است و فقط داخل تراکنش recordPayment جلو می‌رود.
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

    /**
     * سررسید پرداخت (issuedAt + roshdeandishe.finance.default-due-days).
     * در DB عمداً nullable: فاکتورهای صادرشده در dev قبل از این فیلد سررسید
     * ندارند (ddl-auto=update نمی‌تواند ستون not-null بدون default به جدول
     * پر اضافه کند)؛ null یعنی «بدون سررسید» - هرگز OVERDUE نمی‌شود.
     */
    @Column(name = "due_date")
    private Instant dueDate;

    /**
     * default 0 در سطح DB تا ردیف‌های موجود dev هنگام اضافه شدن ستون مقدار
     * درست بگیرند و not-null بماند.
     */
    @Column(name = "amount_paid", nullable = false, precision = 12, scale = 2,
            columnDefinition = "numeric(12,2) default 0 not null")
    private BigDecimal amountPaid;

    /** جمعِ نگه‌داشته‌شده‌ی تخفیف‌ها (InvoiceDiscountEntity)؛ مبلغ قابل پرداخت = amount - discountTotal (V28). */
    @Column(name = "discount_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    /** کدِ canonical سالِ تحصیلی؛ خالی = «به سالی نسبت داده نشده» (academic-year.md فاز ۲). */
    @Column(name = "academic_year", length = 16)
    private String academicYear;

    InvoiceEntity(Long schoolId, Long studentId, BigDecimal amount, Instant issuedAt, Instant dueDate) {
        this.schoolId = schoolId;
        this.studentId = studentId;
        this.amount = amount;
        this.issuedAt = issuedAt;
        this.dueDate = dueDate;
        this.amountPaid = BigDecimal.ZERO;
    }
}
