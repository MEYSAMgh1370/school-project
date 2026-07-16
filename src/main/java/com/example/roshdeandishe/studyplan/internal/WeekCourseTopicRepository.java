package com.example.roshdeandishe.studyplan.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface WeekCourseTopicRepository extends JpaRepository<WeekCourseTopicEntity, Long> {

    List<WeekCourseTopicEntity> findByWeekCourseIdOrderByIdAsc(Long weekCourseId);

    List<WeekCourseTopicEntity> findByWeekCourseIdInOrderByIdAsc(Collection<Long> weekCourseIds);
}
