package com.example.roshdeandishe.notification.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

interface SchoolBotRepository extends JpaRepository<SchoolBotEntity, Long> {

    Optional<SchoolBotEntity> findBySchoolId(Long schoolId);

    Optional<SchoolBotEntity> findByBotUserId(Long botUserId);

    Optional<SchoolBotEntity> findByWebhookSecretHashAndStatus(String hash, SchoolBotStatus status);

    Optional<SchoolBotEntity> findBySchoolIdAndStatus(Long schoolId, SchoolBotStatus status);

    @Query("select b.schoolId from SchoolBotEntity b where b.status = com.example.roshdeandishe.notification.internal.SchoolBotStatus.ACTIVE")
    Set<Long> findActiveSchoolIds();

    /** فقط ستون last_update_at را می‌نویسد، تا تغییر هم‌زمان وضعیت یا توکن توسط مدیر برنگردد. */
    @Modifying
    @Transactional
    @Query("update SchoolBotEntity b set b.lastUpdateAt = :now where b.id = :id")
    int touchLastUpdate(@Param("id") Long id, @Param("now") Instant now);

    /** فقط status و خطای آخر را می‌نویسد (نه کل موجودیت)، تا تغییر هم‌زمانِ مدیر روی فیلدهای دیگر برنگردد. */
    @Modifying
    @Transactional
    @Query("update SchoolBotEntity b set b.status = :status, b.lastError = :error, b.lastErrorAt = :at where b.id = :id")
    int markStatus(@Param("id") Long id, @Param("status") SchoolBotStatus status, @Param("error") String error,
                   @Param("at") Instant at);
}
