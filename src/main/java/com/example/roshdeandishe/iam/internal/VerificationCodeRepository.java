package com.example.roshdeandishe.iam.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface VerificationCodeRepository extends JpaRepository<VerificationCodeEntity, Long> {

    // فقط آخرین کدِ مصرف‌نشده معتبر است - یک درخواست تازه (که همیشه یک
    // ردیف جدید می‌سازد، رجوع به VerificationCodeEntity) عملاً کد قبلی را
    // بی‌اثر می‌کند، حتی اگر آن هنوز منقضی نشده باشد.
    Optional<VerificationCodeEntity> findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(Long userId);
}
