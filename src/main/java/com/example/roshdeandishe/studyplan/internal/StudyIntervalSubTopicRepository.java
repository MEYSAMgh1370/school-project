package com.example.roshdeandishe.studyplan.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface StudyIntervalSubTopicRepository extends JpaRepository<StudyIntervalSubTopicEntity, Long> {

    List<StudyIntervalSubTopicEntity> findByStudyIntervalIdIn(List<Long> studyIntervalIds);
}
