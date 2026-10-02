package com.example.roshdeandishe.academic.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface CourseRepository extends JpaRepository<CourseEntity, Long> {

    List<CourseEntity> findByGradeLevelOrderByNameAsc(int gradeLevel);
}
