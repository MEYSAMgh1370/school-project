package com.example.roshdeandishe.academic.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

interface TimetablePeriodRepository extends JpaRepository<TimetablePeriodEntity, Long> {

    List<TimetablePeriodEntity> findBySchoolIdAndAcademicYearOrderByStartTimeAscPositionAsc(
            Long schoolId, String academicYear);

    List<TimetablePeriodEntity> findByIdIn(Collection<Long> ids);

    @Query("select coalesce(max(p.position), 0) from TimetablePeriodEntity p "
            + "where p.schoolId = :schoolId and p.academicYear = :academicYear")
    int maxPosition(Long schoolId, String academicYear);
}
