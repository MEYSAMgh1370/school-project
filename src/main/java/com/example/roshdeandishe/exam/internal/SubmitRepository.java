package com.example.roshdeandishe.exam.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface SubmitRepository extends JpaRepository<SubmitEntity, Long> {

    Optional<SubmitEntity> findBySectionQuestionIdAndStudentId(Long sectionQuestionId, Long studentId);

    List<SubmitEntity> findBySectionQuestionIdInAndStudentId(List<Long> sectionQuestionIds, Long studentId);

    void deleteBySectionQuestionIdAndStudentId(Long sectionQuestionId, Long studentId);
}
