package com.example.roshdeandishe.iam.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface SchoolEntitlementRepository extends JpaRepository<SchoolEntitlementEntity, Long> {

    Optional<SchoolEntitlementEntity> findBySchoolIdAndModuleIdAndActiveTrue(Long schoolId, Long moduleId);

    // برای upsert در setSchoolEntitlement - بدون فیلتر active، چون ممکن است
    // ردیف از قبل با active=false وجود داشته باشد و فقط بخواهیم آن را فعال کنیم
    Optional<SchoolEntitlementEntity> findBySchoolIdAndModuleId(Long schoolId, Long moduleId);
}
