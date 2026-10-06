package com.example.roshdeandishe.school.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

interface PlatformHolidayRepository extends JpaRepository<PlatformHolidayEntity, Long> {

    List<PlatformHolidayEntity> findByAcademicYearIdOrderByDayAscIdAsc(Long academicYearId);

    boolean existsByDay(LocalDate day);
}
