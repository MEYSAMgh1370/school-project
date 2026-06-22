package com.example.roshdeandishe.iam.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface SchoolEntitlementRepository extends JpaRepository<SchoolEntitlementEntity, Long> {

    Optional<SchoolEntitlementEntity> findBySchoolIdAndModuleIdAndActiveTrue(Long schoolId, Long moduleId);
}
