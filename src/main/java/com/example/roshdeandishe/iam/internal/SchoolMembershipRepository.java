package com.example.roshdeandishe.iam.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface SchoolMembershipRepository extends JpaRepository<SchoolMembershipEntity, Long> {

    // برای نمایش/مدیریت - شامل عضویت‌های غیرفعال هم می‌شود
    List<SchoolMembershipEntity> findByUserIdAndSchoolId(Long userId, Long schoolId);

    // برای تصمیم‌گیری دسترسی واقعی - فقط عضویت‌های فعال
    List<SchoolMembershipEntity> findByUserIdAndSchoolIdAndActiveTrue(Long userId, Long schoolId);

    Optional<SchoolMembershipEntity> findFirstByUserIdAndSchoolId(Long userId, Long schoolId);

    // برای پنل مدیریت مدرسه: "همه‌ی عضویت‌های این مدرسه را نشان بده"
    List<SchoolMembershipEntity> findBySchoolId(Long schoolId);

    // برای idempotent بودن createMembership - اگر همین سه‌تایی از قبل وجود
    // داشته باشد (حتی غیرفعال)، رکورد جدید نمی‌سازیم
    Optional<SchoolMembershipEntity> findByUserIdAndSchoolIdAndRoleId(Long userId, Long schoolId, Long roleId);
}
