package com.example.roshdeandishe.iam.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface RoleRepository extends JpaRepository<RoleEntity, Long> {

    /**
     * فقط برای نقش‌های سیستمی (schoolId = null) معنی دارد - seed runner و
     * چک تداخل کدِ نقش سفارشی با نقش سیستمی هر دو از این استفاده می‌کنند.
     */
    Optional<RoleEntity> findByCodeAndSchoolIdIsNull(String code);

    Optional<RoleEntity> findByCodeAndSchoolId(String code, Long schoolId);

    /**
     * برای resolve کردن roleCode در createMembership/listRoles: هم نقش‌های
     * سیستمی (schoolId = null، برای همه‌ی مدرسه‌ها) هم نقش‌های سفارشیِ خودِ
     * این مدرسه را برمی‌گرداند - نه نقش‌های سفارشیِ مدرسه‌ی دیگر.
     */
    List<RoleEntity> findBySchoolIdIsNullOrSchoolId(Long schoolId);
}
