package com.example.roshdeandishe.studyplan.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface StudyIntervalSubTopicRepository extends JpaRepository<StudyIntervalSubTopicEntity, Long> {

    List<StudyIntervalSubTopicEntity> findByIntervalIdInOrderByIdAsc(Collection<Long> intervalIds);

    void deleteByIntervalId(Long intervalId);

    void deleteByIntervalIdIn(Collection<Long> intervalIds);
}
