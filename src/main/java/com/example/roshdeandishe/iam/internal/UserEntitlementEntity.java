package com.example.roshdeandishe.iam.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * اشتراک شخصی یک کاربر برای ماژول‌های USER-scoped (مثل AI_TUTOR,
 * TEACHER_MARKETPLACE) - کاملاً مستقل از عضویت او در هر مدرسه‌ای.
 * این همان بخش B2C معماری است که در کنار اشتراک‌های B2B (مدرسه‌محور)
 * در {@link SchoolEntitlementEntity} وجود دارد.
 */
@Entity
@Table(
        name = "user_entitlements",
        schema = "iam",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "module_id"})
)
@Getter
@Setter
@NoArgsConstructor
class UserEntitlementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "module_id", nullable = false)
    private Long moduleId;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    UserEntitlementEntity(Long userId, Long moduleId, boolean active) {
        this.userId = userId;
        this.moduleId = moduleId;
        this.active = active;
    }
}
