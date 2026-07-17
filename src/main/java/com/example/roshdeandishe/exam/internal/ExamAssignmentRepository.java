package com.example.roshdeandishe.exam.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface ExamAssignmentRepository extends JpaRepository<ExamAssignmentEntity, Long> {

    List<ExamAssignmentEntity> findByExamIdOrderByIdAsc(Long examId);

    List<ExamAssignmentEntity> findByStudentIdOrderByIdAsc(Long studentId);

    boolean existsByExamIdAndStudentId(Long examId, Long studentId);
}
