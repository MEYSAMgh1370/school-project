package com.example.roshdeandishe.questionbox.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface QuestionImageRepository extends JpaRepository<QuestionImageEntity, Long> {

    List<QuestionImageEntity> findByQuestionIdOrderByIdAsc(Long questionId);

    List<QuestionImageEntity> findByQuestionIdInOrderByIdAsc(Collection<Long> questionIds);

    long countByQuestionId(Long questionId);
}
