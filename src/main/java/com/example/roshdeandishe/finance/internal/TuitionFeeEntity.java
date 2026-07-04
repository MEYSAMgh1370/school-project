package com.example.roshdeandishe.finance.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * شهریه‌ی مصوب یک مدرسه - منبع قیمت‌گذاری واقعی که issueTuitionInvoice از
 * آن مبلغ فاکتور را می‌خواند (جایگزین مقدار صفر hardcode شده‌ی قبلی).
 * <p>
 * عمداً یک ردیف به‌ازای هر مدرسه (unique روی school_id)، نه به‌ازای هر
 * کلاس/پایه - قیمت‌گذاری پیچیده‌تر (per-class, per-grade، تخفیف) هنوز
 * توسط هیچ‌کس خواسته نشده و طبق YAGNI ساخته نمی‌شود.
 */
@Entity
@Table(
        name = "tuition_fees",
        schema = "finance",
        uniqueConstraints = @UniqueConstraint(columnNames = {"school_id"})
)
@Getter
@Setter
@NoArgsConstructor
class TuitionFeeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    TuitionFeeEntity(Long schoolId, BigDecimal amount) {
        this.schoolId = schoolId;
        this.amount = amount;
    }
}
