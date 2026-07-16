package com.example.roshdeandishe.curriculum.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface QuestionRepository extends JpaRepository<QuestionEntity, Long> {

    List<QuestionEntity> findBySchoolIdOrderByIdAsc(Long schoolId);

    List<QuestionEntity> findBySchoolIdAndTopicIdInOrderByIdAsc(Long schoolId, Collection<Long> topicIds);
}
