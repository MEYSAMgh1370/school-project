package com.example.roshdeandishe.academic.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface StudentGuardianRepository extends JpaRepository<StudentGuardianEntity, Long> {

    // برای idempotent بودن linkGuardian - همان (student, guardian) نباید دوبار لینک شود
    Optional<StudentGuardianEntity> findByStudentIdAndGuardianUserId(Long studentId, Long guardianUserId);

    List<StudentGuardianEntity> findByStudentId(Long studentId);

    List<StudentGuardianEntity> findByGuardianUserIdAndSchoolId(Long guardianUserId, Long schoolId);
}
