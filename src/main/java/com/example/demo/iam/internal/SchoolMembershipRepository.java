package com.example.roshdeandishe.iam.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface SchoolMembershipRepository extends JpaRepository<SchoolMembershipEntity, Long> {

    // یک کاربر ممکن است در یک مدرسه چند نقش داشته باشد (مثلاً هم Teacher هم SchoolAdmin)
    List<SchoolMembershipEntity> findByUserIdAndSchoolId(Long userId, Long schoolId);

    Optional<SchoolMembershipEntity> findFirstByUserIdAndSchoolId(Long userId, Long schoolId);
}
