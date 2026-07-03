package com.example.roshdeandishe.iam.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * نقش به‌عنوان یک "مسئولیت قابل تخصیص" مدل شده، نه به‌عنوان نوع کاربر.
 * این یعنی اضافه کردن نقش جدید در آینده فقط یک ردیف داده است، نه تغییر کد.
 * <p>
 * مقادیر اولیه توسط {@link IamSeedDataRunner} درج می‌شوند:
 * STUDENT, TEACHER, PARENT, SCHOOL_ADMIN, PLATFORM_ADMIN
 */
@Entity
@Table(name = "roles", schema = "iam")
@Getter
@Setter
@NoArgsConstructor
class RoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    RoleEntity(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }
}
