package com.example.roshdeandishe.studyplan.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

interface WeekCourseRepository extends JpaRepository<WeekCourseEntity, Long> {

    List<WeekCourseEntity> findByPlanIdOrderByWeekStartDateAscIdAsc(Long planId);

    List<WeekCourseEntity> findByPlanIdAndWeekStartDate(Long planId, LocalDate weekStartDate);
}
