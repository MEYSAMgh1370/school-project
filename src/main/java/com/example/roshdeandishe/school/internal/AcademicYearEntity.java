package com.example.roshdeandishe.school.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * یک سال تحصیلی در فهرست سراسری (openspec/changes/academic-year.md): مشترک بین
 * همه‌ی مدرسه‌ها (بدون {@code schoolId}) و فقط‌خواندنی در برنامه - ردیف‌ها را
 * اپراتور پلتفرم با مهاجرت داده مدیریت می‌کند، پس عمداً setter و سازنده‌ی
 * عمومی ندارد. {@code code} شکل متداول («1404-1405»، رقم ASCII) و
 * {@code startYear} برای ترتیب‌دهی است.
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
