package com.example.roshdeandishe.iam.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * نقش به‌عنوان یک "مسئولیت قابل تخصیص" مدل شده، نه به‌عنوان نوع کاربر.
 * این یعنی اضافه کردن نقش جدید در آینده فقط یک ردیف داده است، نه تغییر کد.
 * <p>
 * مقادیر اولیه‌ی سیستمی (schoolId = null) توسط {@link IamSeedDataRunner}
 * درج می‌شوند: STUDENT, TEACHER, PARENT, SCHOOL_ADMIN, FINANCE_ADMIN,
 * PLATFORM_ADMIN.
 * <p>
 * {@code schoolId} (اضافه‌شده ۲۰۲۶-۰۷-۱۳، حل‌کننده‌ی Open Question 3 در
 * CLAUDE.md): {@code null} یعنی نقش سیستمی (برای همه‌ی مدرسه‌ها یکسان و
 * ثابت)، مقدار غیر-null یعنی نقشِ سفارشیِ همان مدرسه‌ی خاص - فقط همان
 * مدرسه می‌تواند این نقش را ببیند/تخصیص دهد (بدون نشتِ نقش‌های سفارشی بین
 * مستأجرها). محدودیت unique از تک‌ستونی روی {@code code} به ترکیبی
 * {@code (code, school_id)} تغییر کرده - این یعنی دو مدرسه‌ی مختلف می‌توانند
 * هر دو یک نقش سفارشی با کد یکسان بسازند (مثلاً هر دو "LIBRARIAN")، بدون
 * تداخل با هم؛ اعتبارسنجیِ «این کد با یکی از نقش‌های سیستمی تداخل ندارد»
 * در {@code IamFacadeImpl.createCustomRole} انجام می‌شود، نه در دیتابیس -
 * چون محدودیت دیتابیس با NULL در ستون school_id نمی‌تواند این تداخل را
 * تشخیص دهد (چند ردیف با school_id=NULL طبق قرارداد SQL معادل هم شمرده
 * نمی‌شوند).
 */
@Entity
@Table(
        name = "roles",
        schema = "iam",
        uniqueConstraints = @UniqueConstraint(columnNames = {"code", "school_id"})
)
@Getter
@Setter
@NoArgsConstructor
class RoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "school_id")
    private Long schoolId;

    RoleEntity(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    RoleEntity(String code, String displayName, Long schoolId) {
        this.code = code;
        this.displayName = displayName;
        this.schoolId = schoolId;
    }
}
