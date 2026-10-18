package com.example.roshdeandishe.notification.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * رباتِ اختصاصی یک مدرسه. schoolId و شناسه‌های کاربر فقط شناسه‌ی عددیِ مات‌اند (بدون FK به ماژول‌های دیگر).
 * توکن فقط رمزشده و رازِ وب‌هوک فقط به‌صورت هش ذخیره می‌شود.
 */
@Entity
@Table(name = "school_bots", schema = "bale")
@Getter
@Setter
@NoArgsConstructor
class SchoolBotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false, unique = true)
    private Long schoolId;

    @Column(name = "bot_user_id", nullable = false, unique = true)
    private Long botUserId;

    @Column(name = "bot_username", nullable = false)
    private String botUsername;

    @Column(name = "token_ciphertext", nullable = false)
    private String tokenCiphertext;

    @Column(name = "token_key_version", nullable = false)
    private int tokenKeyVersion;

    @Column(name = "webhook_secret_hash", nullable = false, unique = true, length = 64)
    private String webhookSecretHash;

    @Column(name = "business_enabled", nullable = false)
    private boolean businessEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SchoolBotStatus status;

    @Column(name = "last_update_at")
    private Instant lastUpdateAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "last_error_at")
    private Instant lastErrorAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
