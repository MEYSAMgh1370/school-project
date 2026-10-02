package com.example.roshdeandishe.school.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * یک سال تحصیلی در فهرست سراسری (openspec/changes/academic-year.md): مشترک بین
 * همه‌ی مدرسه‌ها (بدون {@code schoolId}). ردیف‌ها را فقط AcademicYearProvisioner
 * می‌سازد (هر سال، در تاریخِ شمسیِ کانفیگ) و مدرسه‌ها فقط می‌خوانند، پس عمداً
 * setter و سازنده‌ی عمومی ندارد. {@code code} شکل متداول («1404-1405»، رقم
 * ASCII) و {@code startYear} برای ترتیب‌دهی و یکتایی است. سال تاریخ
 * شروع/پایان ندارد.
 */
@Entity
@Table(name = "academic_years", schema = "school")
@Getter
@NoArgsConstructor
class AcademicYearEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String code;

    @Column(name = "start_year", nullable = false)
    private int startYear;
}
