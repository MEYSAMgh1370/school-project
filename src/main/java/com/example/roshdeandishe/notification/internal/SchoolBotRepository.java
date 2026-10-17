package com.example.roshdeandishe.notification.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.Set;

interface SchoolBotRepository extends JpaRepository<SchoolBotEntity, Long> {

    Optional<SchoolBotEntity> findBySchoolId(Long schoolId);

    Optional<SchoolBotEntity> findByBotUserId(Long botUserId);

    Optional<SchoolBotEntity> findByWebhookSecretHashAndStatus(String hash, SchoolBotStatus status);

    Optional<SchoolBotEntity> findBySchoolIdAndStatus(Long schoolId, SchoolBotStatus status);

    @Query("select b.schoolId from SchoolBotEntity b where b.status = com.example.roshdeandishe.notification.internal.SchoolBotStatus.ACTIVE")
    Set<Long> findActiveSchoolIds();
}
