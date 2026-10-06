package com.example.roshdeandishe.iam.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByEmail(String email);

    Optional<UserEntity> findByPhoneNumber(String phoneNumber);

    /**
     * ایمیل‌ها در ثبت‌نام نرمال (lower-case) نمی‌شوند و یکتایی case-sensitive است، پس چند ردیف
     * ممکن است فقط در حروف بزرگ/کوچک فرق کنند - فقط برای PlatformAdminProvisioner که ابهام را رد می‌کند.
     */
    List<UserEntity> findAllByEmailIgnoreCase(String email);
}
