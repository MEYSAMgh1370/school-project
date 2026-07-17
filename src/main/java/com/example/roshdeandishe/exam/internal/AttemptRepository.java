package com.example.roshdeandishe.exam.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface AttemptRepository extends JpaRepository<AttemptEntity, Long> {

    Optional<AttemptEntity> findByExamIdAndStudentId(Long examId, Long studentId);

    List<AttemptEntity> findByExamIdOrderByStartedAtDesc(Long examId);
}
