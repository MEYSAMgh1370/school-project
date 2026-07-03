package com.example.roshdeandishe.iam.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * یک Permission یک "قابلیت" واقعی است (مثل FINANCE_MANAGE, GRADES_EDIT)،
 * در حالی که Role فقط یک نام/برچسب است. این جداسازی دقیقاً همان چیزی است
 * که اجازه می‌دهد دو Role متفاوت (مثلاً SCHOOL_ADMIN کامل در مقابل
 * FINANCE_ADMIN محدود) سطح دسترسی متفاوتی داشته باشند، بدون نوشتن کد جدید -
 * فقط با تغییر داده در جدول {@link RolePermissionEntity}.
 */
@Entity
@Table(name = "permissions", schema = "iam")
@Getter
@Setter
@NoArgsConstructor
class PermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    PermissionEntity(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }
}
