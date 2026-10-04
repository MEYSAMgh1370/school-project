package com.example.roshdeandishe.academic.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface EnrollmentRepository extends JpaRepository<EnrollmentEntity, Long> {

    Optional<EnrollmentEntity> findByStudentIdAndAcademicYear(Long studentId, String academicYear);

    List<EnrollmentEntity> findBySchoolIdAndAcademicYear(Long schoolId, String academicYear);

    List<EnrollmentEntity> findBySchoolIdAndAcademicYearAndStatus(Long schoolId, String academicYear,
                                                                  EnrollmentEntity.Status status);

    List<EnrollmentEntity> findByPromotionRunId(Long promotionRunId);

    List<EnrollmentEntity> findByClassId(Long classId);

    boolean existsByStudentIdAndClassId(Long studentId, Long classId);

    boolean existsBySchoolId(Long schoolId);

    List<EnrollmentEntity> findByStudentIdIn(Collection<Long> studentIds);
}
