package com.example.roshdeandishe.iam.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity داخلی - خارج از ماژول iam قابل دسترسی نیست.
 * نگاشت به جدول {@code iam.school_modules} که مشخص می‌کند کدام ماژول‌ها
 * برای کدام مدرسه (schoolId) فعال هستند.
 */
@Entity
@Table(
        name = "school_modules",
        schema = "iam",
        uniqueConstraints = @UniqueConstraint(columnNames = {"school_id", "module_name"})
)
@Getter
@Setter
@NoArgsConstructor
class SchoolModuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "module_name", nullable = false, length = 64)
    private String moduleName;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    SchoolModuleEntity(Long schoolId, String moduleName, boolean enabled) {
        this.schoolId = schoolId;
        this.moduleName = moduleName;
        this.enabled = enabled;
    }
}
