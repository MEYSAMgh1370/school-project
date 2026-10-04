package com.example.roshdeandishe.academic.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface StudentRepository extends JpaRepository<StudentEntity, Long> {

    // برای idempotent بودن enrollStudent - یک کاربر نباید دوبار در همان
    // مدرسه به‌عنوان دانش‌آموز ثبت‌نام شود
    Optional<StudentEntity> findByUserIdAndSchoolId(Long userId, Long schoolId);

    List<StudentEntity> findBySchoolId(Long schoolId);

    List<StudentEntity> findBySchoolIdAndStatusIn(Long schoolId, java.util.Collection<com.example.roshdeandishe.academic.StudentStatus> statuses);

    /**
     * دانش‌آموزِ ACTIVE/PENDING که هیچ ردیفِ ثبت‌نامی ندارد: «انتساب‌نشده». مدرسه‌ای که هیچ‌کدام ندارد و
     * سالِ جاری دارد «ردیاب‌شده» است (academic-year.md فاز ۳).
     */
    @org.springframework.data.jpa.repository.Query("select count(s) from StudentEntity s where s.schoolId = :schoolId "
            + "and s.status in (com.example.roshdeandishe.academic.StudentStatus.ACTIVE, com.example.roshdeandishe.academic.StudentStatus.PENDING) "
            + "and not exists (select 1 from EnrollmentEntity e where e.studentId = s.id)")
    long countWithoutEnrollment(@org.springframework.data.repository.query.Param("schoolId") Long schoolId);

    @org.springframework.data.jpa.repository.Query("select s from StudentEntity s where s.schoolId = :schoolId "
            + "and s.status in (com.example.roshdeandishe.academic.StudentStatus.ACTIVE, com.example.roshdeandishe.academic.StudentStatus.PENDING) "
            + "and not exists (select 1 from EnrollmentEntity e where e.studentId = s.id)")
    List<StudentEntity> findWithoutEnrollment(@org.springframework.data.repository.query.Param("schoolId") Long schoolId);
}
