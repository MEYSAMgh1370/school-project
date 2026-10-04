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
 * یک ردیف به‌ازای هر (مدرسه، سال تحصیلی) - نه به‌ازای هر کلاس/پایه؛ قیمت‌گذاری پیچیده‌تر
 * (per-class, per-grade، تخفیف) هنوز توسط هیچ‌کس خواسته نشده و طبق YAGNI ساخته نمی‌شود.
 * {@code academicYear} خالی = «به سالی نسبت داده نشده» (ردیف‌های قبل از فاز ۲ و مدرسه‌ای که سالِ
 * جاری ندارد)؛ یکتایی با ایندکس‌های جزئیِ V10 است (academic-year.md فاز ۲).
 */
@Entity
@Table(name = "tuition_fees", schema = "finance")
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

    @Column(name = "academic_year", length = 16)
    private String academicYear;

    TuitionFeeEntity(Long schoolId, String academicYear, BigDecimal amount) {
        this.schoolId = schoolId;
        this.academicYear = academicYear;
        this.amount = amount;
    }
}
