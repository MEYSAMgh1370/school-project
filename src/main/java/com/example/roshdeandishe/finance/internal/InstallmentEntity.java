package com.example.roshdeandishe.finance.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * یک قسطِ فاکتور شهریه (V29). وضعیت ذخیره نمی‌شود؛ مشتق از (amount, amountPaid, dueDate) است.
 * جمعِ amountPaid اقساطِ یک فاکتور همیشه برابر InvoiceEntity.amountPaid است (تخصیصِ
 * FinanceFacadeImpl داخل همان تراکنش و قفلِ پرداخت).
 */
@Entity
@Table(name = "installments", schema = "finance")
@Getter
@Setter
@NoArgsConstructor
class InstallmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "amount_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    /** خالی = «بدون سررسید» (V31): هرگز دیرکرد نمی‌شود و در ترتیب‌ها آخر می‌آید. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    InstallmentEntity(Long schoolId, Long invoiceId, BigDecimal amount, LocalDate dueDate) {
        this.schoolId = schoolId;
        this.invoiceId = invoiceId;
        this.amount = amount;
        this.dueDate = dueDate;
    }

    /** قسط «پرداخت‌شده» است وقتی کاملاً پرداخت شده؛ چنین قسطی ثابت است و هرگز ویرایش/حذف نمی‌شود. */
    boolean isPaid() {
        return amountPaid.compareTo(amount) >= 0;
    }
}
