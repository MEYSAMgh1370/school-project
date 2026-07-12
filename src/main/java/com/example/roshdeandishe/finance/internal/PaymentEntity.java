package com.example.roshdeandishe.finance.internal;

import com.example.roshdeandishe.finance.PaymentMethod;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * یک پرداختِ ثبت‌شده روی یک فاکتور. جدول جدا (نه ستون روی InvoiceEntity)
 * چون یک فاکتور می‌تواند چند پرداخت قسطی داشته باشد؛ جمع پرداخت‌ها به‌صورت
 * تراکنشی روی InvoiceEntity.amountPaid نگه داشته می‌شود تا وضعیت فاکتور
 * بدون aggregate کردن این جدول قابل مشتق شدن باشد.
 * <p>
 * {@code recordedByUserId} همان قرارداد nullable بودن actorUserId در
 * رویدادهاست (رجوع به InvoiceIssuedEvent).
 */
@Entity
@Table(name = "payments", schema = "finance")
@Getter
@Setter
@NoArgsConstructor
class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentMethod method;

    @Column(length = 512)
    private String note;

    @Column(name = "recorded_by_user_id")
    private Long recordedByUserId;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    PaymentEntity(Long invoiceId, Long schoolId, BigDecimal amount, PaymentMethod method,
                  String note, Long recordedByUserId, Instant paidAt) {
        this.invoiceId = invoiceId;
        this.schoolId = schoolId;
        this.amount = amount;
        this.method = method;
        this.note = note;
        this.recordedByUserId = recordedByUserId;
        this.paidAt = paidAt;
    }
}
