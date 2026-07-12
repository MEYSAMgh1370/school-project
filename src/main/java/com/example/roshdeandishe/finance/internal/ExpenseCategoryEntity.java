package com.example.roshdeandishe.finance.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * دسته‌ی هزینه - per-school (هر مدرسه دسته‌های خودش را دارد و می‌تواند
 * دسته‌ی اختصاصی بسازد؛ «دسته‌های پیش‌فرض» فقط یک seed idempotent روی همین
 * جدول است - ExpenseFacade.seedDefaultCategories - نه یک جدول سراسری).
 * غیرفعال‌سازی به‌جای حذف: دسته‌ای که هزینه‌ی ثبت‌شده دارد نباید ناپدید شود.
 */
@Entity
@Table(
        name = "expense_categories",
        schema = "finance",
        uniqueConstraints = @UniqueConstraint(columnNames = {"school_id", "name"})
)
@Getter
@Setter
@NoArgsConstructor
class ExpenseCategoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false)
    private boolean active;

    ExpenseCategoryEntity(Long schoolId, String name) {
        this.schoolId = schoolId;
        this.name = name;
        this.active = true;
    }
}
