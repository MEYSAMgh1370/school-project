package com.example.roshdeandishe.school.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface AcademicYearRepository extends JpaRepository<AcademicYearEntity, Long> {

    List<AcademicYearEntity> findAllByOrderByStartYearAsc();
}
