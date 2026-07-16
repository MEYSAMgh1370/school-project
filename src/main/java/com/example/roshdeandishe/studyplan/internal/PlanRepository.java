package com.example.roshdeandishe.studyplan.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface PlanRepository extends JpaRepository<PlanEntity, Long> {

    List<PlanEntity> findBySchoolIdOrderByIdAsc(Long schoolId);
}
