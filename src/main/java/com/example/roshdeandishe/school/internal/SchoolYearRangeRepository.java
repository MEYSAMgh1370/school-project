package com.example.roshdeandishe.school.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface SchoolYearRangeRepository extends JpaRepository<SchoolYearRangeEntity, SchoolYearRangeEntity.Key> {

    Optional<SchoolYearRangeEntity> findBySchoolIdAndAcademicYearId(Long schoolId, Long academicYearId);
}
