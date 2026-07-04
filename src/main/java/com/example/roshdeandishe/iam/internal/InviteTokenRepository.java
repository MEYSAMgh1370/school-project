package com.example.roshdeandishe.iam.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

interface InviteTokenRepository extends JpaRepository<InviteTokenEntity, Long> {

    Optional<InviteTokenEntity> findByToken(String token);

    // برای idempotent بودن issueInviteToken: اگر یک توکن معتبر و
    // redeem-نشده از قبل برای این کاربر وجود دارد، همان را دوباره
    // برمی‌گردانیم به‌جای ساخت یک توکن جدید در هر بار درخواست
    Optional<InviteTokenEntity> findFirstByUserIdAndRedeemedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            Long userId, Instant now
    );
}
