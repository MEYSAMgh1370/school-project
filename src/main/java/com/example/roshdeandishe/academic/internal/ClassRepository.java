package com.example.roshdeandishe.academic.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface ClassRepository extends JpaRepository<ClassEntity, Long> {

    List<ClassEntity> findBySchoolId(Long schoolId);

    /** کلاس‌های یک سال به‌علاوه‌ی «بدون سال»ها. */
    @org.springframework.data.jpa.repository.Query("select c from ClassEntity c where c.schoolId = :schoolId "
            + "and (c.academicYear = :year or c.academicYear is null)")
    List<ClassEntity> findBySchoolIdAndYearOrUnattributed(
            @org.springframework.data.repository.query.Param("schoolId") Long schoolId,
            @org.springframework.data.repository.query.Param("year") String year);

    long countBySchoolIdAndAcademicYearIsNull(Long schoolId);
}
