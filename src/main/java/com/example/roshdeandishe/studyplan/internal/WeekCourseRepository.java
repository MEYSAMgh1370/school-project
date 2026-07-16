package com.example.roshdeandishe.studyplan.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

interface WeekCourseRepository extends JpaRepository<WeekCourseEntity, Long> {

    List<WeekCourseEntity> findByPlanIdOrderByWeekStartAscIdAsc(Long planId);

    Optional<WeekCourseEntity> findByPlanIdAndWeekStartAndCourseTopicId(Long planId, LocalDate weekStart,
                                                                        Long courseTopicId);
}
