package com.example.roshdeandishe.finance.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface TuitionFeeRepository extends JpaRepository<TuitionFeeEntity, Long> {

    Optional<TuitionFeeEntity> findBySchoolIdAndAcademicYear(Long schoolId, String academicYear);

    /** شهریه‌ی «بدون سال» (قبل از فاز ۲ یا مدرسه‌ای که سالِ جاری ندارد). */
    Optional<TuitionFeeEntity> findBySchoolIdAndAcademicYearIsNull(Long schoolId);

    @Modifying
    @Query("update TuitionFeeEntity f set f.academicYear = :academicYear "
            + "where f.schoolId = :schoolId and f.academicYear is null")
    int attributeToYear(@Param("schoolId") Long schoolId, @Param("academicYear") String academicYear);
}
