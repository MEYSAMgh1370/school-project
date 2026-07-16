package com.example.roshdeandishe.studyplan.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface StudentPlanAssignmentRepository extends JpaRepository<StudentPlanAssignmentEntity, Long> {

    Optional<StudentPlanAssignmentEntity> findBySchoolIdAndStudentId(Long schoolId, Long studentId);
}
