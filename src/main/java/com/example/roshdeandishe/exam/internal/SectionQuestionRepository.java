package com.example.roshdeandishe.exam.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface SectionQuestionRepository extends JpaRepository<SectionQuestionEntity, Long> {

    List<SectionQuestionEntity> findBySectionIdOrderByIdAsc(Long sectionId);

    List<SectionQuestionEntity> findBySectionIdInOrderByIdAsc(List<Long> sectionIds);

    Optional<SectionQuestionEntity> findBySectionIdAndQuestionId(Long sectionId, Long questionId);

    boolean existsBySectionIdAndQuestionId(Long sectionId, Long questionId);
}
