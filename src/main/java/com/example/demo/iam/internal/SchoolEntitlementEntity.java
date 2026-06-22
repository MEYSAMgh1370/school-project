package com.example.roshdeandishe.iam.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * جایگزین مستقیم جدول قبلی {@code school_modules}. هر ردیف یعنی
 * "این مدرسه، این ماژول SCHOOL-scoped را خریده/فعال کرده است".
 * <p>
 * این Entity فقط برای ماژول‌هایی با {@code ModuleEntity.scope == SCHOOL}
 * معنا دارد (مثل ACADEMIC, FINANCE). برای ماژول‌های USER-scoped باید از
 * {@link UserEntitlementEntity} استفاده شود.
 */
@Entity
@Table(
        name = "school_entitlements",
        schema = "iam",
        uniqueConstraints = @UniqueConstraint(columnNames = {"school_id", "module_id"})
)
@Getter
@Setter
@NoArgsConstructor
class SchoolEntitlementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "module_id", nullable = false)
    private Long moduleId;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    SchoolEntitlementEntity(Long schoolId, Long moduleId, boolean active) {
        this.schoolId = schoolId;
        this.moduleId = moduleId;
        this.active = active;
    }
}
