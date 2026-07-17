package com.example.roshdeandishe.exam.internal;

import com.example.roshdeandishe.exam.ExamType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

interface ExamRepository extends JpaRepository<ExamEntity, Long> {

    List<ExamEntity> findBySchoolIdAndExamTypeOrderByIdDesc(Long schoolId, ExamType examType);

    Optional<ExamEntity> findByStudentIdAndExamTypeAndWeekStart(Long studentId, ExamType examType, LocalDate weekStart);

    List<ExamEntity> findByStudentIdAndExamTypeOrderByWeekStartDesc(Long studentId, ExamType examType);
}
