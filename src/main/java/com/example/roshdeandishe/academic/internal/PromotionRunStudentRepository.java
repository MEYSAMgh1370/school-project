package com.example.roshdeandishe.academic.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface PromotionRunStudentRepository extends JpaRepository<PromotionRunStudentEntity, Long> {

    List<PromotionRunStudentEntity> findByRunId(Long runId);

    Optional<PromotionRunStudentEntity> findByRunIdAndStudentId(Long runId, Long studentId);
}
