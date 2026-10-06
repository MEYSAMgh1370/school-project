package com.example.roshdeandishe.iam.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * ردیف «این کاربر مدیر پلتفرم است» (V17). شناسه همان {@code userId} است و ردیف فقط توسط
 * {@link PlatformAdminProvisioner} ساخته/حذف می‌شود - نه عضویت مدرسه است و نه نقش.
 */
@Entity
@Table(name = "platform_admins", schema = "iam")
@Getter
@NoArgsConstructor
class PlatformAdminEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    PlatformAdminEntity(Long userId) {
        this.userId = userId;
        this.createdAt = Instant.now();
    }
}
