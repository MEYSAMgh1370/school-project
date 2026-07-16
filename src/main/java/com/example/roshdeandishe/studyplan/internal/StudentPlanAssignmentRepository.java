package com.example.roshdeandishe.studyplan.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface StudentPlanAssignmentRepository extends JpaRepository<StudentPlanAssignmentEntity, Long> {

    Optional<StudentPlanAssignmentEntity> findByStudentId(Long studentId);

    List<StudentPlanAssignmentEntity> findByPlanIdOrderByIdAsc(Long planId);
}
