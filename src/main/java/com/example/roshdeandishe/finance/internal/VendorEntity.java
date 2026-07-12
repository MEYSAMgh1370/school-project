package com.example.roshdeandishe.finance.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * طرف حساب/فروشنده‌ی هزینه‌ها (شرکت برق، فروشگاه تجهیزات...) - per-school،
 * مثل ExpenseCategoryEntity. همه‌ی فیلدهای شناسنامه‌ای اختیاری‌اند جز نام؛
 * غیرفعال‌سازی به‌جای حذف، همان دلیل دسته‌ها.
 */
@Entity
@Table(name = "vendors", schema = "finance")
@Getter
@Setter
@NoArgsConstructor
class VendorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 32)
    private String phone;

    @Column(name = "national_id", length = 32)
    private String nationalId;

    @Column(name = "economic_code", length = 32)
    private String economicCode;

    @Column(length = 512)
    private String address;

    @Column(nullable = false)
    private boolean active;

    VendorEntity(Long schoolId, String name, String phone, String nationalId,
                 String economicCode, String address) {
        this.schoolId = schoolId;
        this.name = name;
        this.phone = phone;
        this.nationalId = nationalId;
        this.economicCode = economicCode;
        this.address = address;
        this.active = true;
    }
}
