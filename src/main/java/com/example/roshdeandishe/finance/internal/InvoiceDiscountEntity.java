package com.example.roshdeandishe.finance.internal;

import com.example.roshdeandishe.finance.DiscountStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * یک تخفیفِ ثبت‌شده روی فاکتور (append-only). جمعِ تخفیف‌ها تراکنشی روی
 * InvoiceEntity.discountTotal نگه داشته می‌شود، مثل amountPaid و پرداخت‌ها.
 */
@Entity
@Table(name = "invoice_discounts", schema = "finance")
@Getter
@Setter
@NoArgsConstructor
class InvoiceDiscountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 255)
    private String reason;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** آخرِ روزِ مهلت به وقتِ مدرسه؛ خالی = تخفیفِ بی‌قیدوشرط (V30). */
    @Column
    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private DiscountStatus status = DiscountStatus.APPLIED;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    InvoiceDiscountEntity(Long schoolId, Long invoiceId, BigDecimal amount, String reason,
                          Long createdByUserId, Instant createdAt, LocalDate deadline) {
        this.schoolId = schoolId;
        this.invoiceId = invoiceId;
        this.amount = amount;
        this.reason = reason;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
        this.deadline = deadline;
        this.status = deadline == null ? DiscountStatus.APPLIED : DiscountStatus.PENDING;
    }
}
