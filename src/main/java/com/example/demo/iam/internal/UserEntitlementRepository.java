package com.example.roshdeandishe.iam.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface UserEntitlementRepository extends JpaRepository<UserEntitlementEntity, Long> {

    Optional<UserEntitlementEntity> findByUserIdAndModuleIdAndActiveTrue(Long userId, Long moduleId);
}
