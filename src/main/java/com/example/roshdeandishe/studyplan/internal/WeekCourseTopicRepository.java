package com.example.roshdeandishe.studyplan.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface WeekCourseTopicRepository extends JpaRepository<WeekCourseTopicEntity, Long> {

    List<WeekCourseTopicEntity> findByWeekCourseIdOrderBySortOrderAsc(Long weekCourseId);

    List<WeekCourseTopicEntity> findByWeekCourseIdIn(List<Long> weekCourseIds);
}
