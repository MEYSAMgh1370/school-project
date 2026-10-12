package com.example.roshdeandishe.iam.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, Long> {

    Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

    /** آخرین توکنِ کاربر، برای مهلتِ بینِ دو درخواست (cooldown). */
    Optional<PasswordResetTokenEntity> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    /** همه‌ی توکن‌های هنوز-استفاده‌نشده‌ی کاربر را باطل می‌کند؛ هر لحظه حداکثر یک توکنِ قابل‌استفاده می‌ماند. */
    @Transactional
    @Modifying
    @Query("update PasswordResetTokenEntity t set t.usedAt = :now where t.userId = :userId and t.usedAt is null")
    int invalidateOpenTokens(@Param("userId") Long userId, @Param("now") Instant now);
}
