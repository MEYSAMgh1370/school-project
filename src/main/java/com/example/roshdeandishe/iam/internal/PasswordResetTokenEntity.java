package com.example.roshdeandishe.iam.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * توکن یک‌بارمصرفِ بازیابی رمز عبور (password-reset-by-email.md). فقط هشِ SHA-256 توکن ذخیره می‌شود؛
 * مقدارِ خام فقط در ایمیل است، پس نشتِ پایگاه داده لینکِ قابل‌استفاده نمی‌دهد. جدا از
 * {@link InviteTokenEntity} (که عمرش هفت روز است و خام ذخیره می‌شود).
 */
@Entity
@Table(name = "password_reset_tokens", schema = "iam")
@Getter
@Setter
@NoArgsConstructor
class PasswordResetTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    PasswordResetTokenEntity(Long userId, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    boolean isUsable(Instant now) {
        return usedAt == null && !now.isAfter(expiresAt);
    }
}
