package com.example.roshdeandishe.iam.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * توکن یک‌بارمصرف برای فعال‌سازی حساب کاربرانی که با
 * {@code IamFacade.findOrCreateUser} ساخته شده‌اند و هنوز passwordHash
 * ندارند (نگاه کنید به {@link UserEntity}). به ازای هر کاربر - نه هر
 * Membership - چون رمز عبور متعلق به کاربر است، نه یک عضویت خاص.
 */
@Entity
@Table(name = "invite_tokens", schema = "iam")
@Getter
@Setter
@NoArgsConstructor
class InviteTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "redeemed_at")
    private Instant redeemedAt;

    InviteTokenEntity(Long userId, String token, Instant createdAt, Instant expiresAt) {
        this.userId = userId;
        this.token = token;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    boolean isRedeemed() {
        return redeemedAt != null;
    }
}
