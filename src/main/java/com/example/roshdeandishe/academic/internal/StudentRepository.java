package com.example.roshdeandishe.academic.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface StudentRepository extends JpaRepository<StudentEntity, Long> {

    // برای idempotent بودن enrollStudent - یک کاربر نباید دوبار در همان
    // مدرسه به‌عنوان دانش‌آموز ثبت‌نام شود
    Optional<StudentEntity> findByUserIdAndSchoolId(Long userId, Long schoolId);
}
