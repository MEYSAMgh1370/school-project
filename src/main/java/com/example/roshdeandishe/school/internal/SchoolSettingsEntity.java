package com.example.roshdeandishe.school.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * تنظیماتِ یک مدرسه (V34): اقساط، ارتقای خودکار و ربات. ردیف در اولین خواندن از ستون‌های قدیمیِ
 * school.schools ساخته می‌شود (SchoolSettingsRepository.createIfMissing).
 */
@Entity
@Table(name = "school_settings", schema = "school")
@Getter
@Setter
@NoArgsConstructor
class SchoolSettingsEntity {

    @Id
    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "installments_enabled", nullable = false)
    private boolean installmentsEnabled;

    @Column(name = "auto_promotion", nullable = false)
    private boolean autoPromotion;

    @Column(name = "bot_enabled", nullable = false)
    private boolean botEnabled = true;

    @Column(name = "notifications_enabled", nullable = false)
    private boolean notificationsEnabled = true;
}
