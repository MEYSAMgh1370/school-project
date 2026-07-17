package com.example.roshdeandishe.exam.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface SectionRepository extends JpaRepository<SectionEntity, Long> {

    List<SectionEntity> findByExamIdOrderByIdAsc(Long examId);

    Optional<SectionEntity> findByExamIdAndTopicId(Long examId, Long topicId);
}
