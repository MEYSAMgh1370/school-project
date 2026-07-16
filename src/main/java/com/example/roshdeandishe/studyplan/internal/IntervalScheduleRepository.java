package com.example.roshdeandishe.studyplan.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface IntervalScheduleRepository extends JpaRepository<IntervalScheduleEntity, Long> {

    Optional<IntervalScheduleEntity> findBySchoolIdAndStudentId(Long schoolId, Long studentId);
}
