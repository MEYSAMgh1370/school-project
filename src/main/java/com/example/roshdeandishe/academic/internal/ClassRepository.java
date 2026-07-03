package com.example.roshdeandishe.academic.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface ClassRepository extends JpaRepository<ClassEntity, Long> {

    List<ClassEntity> findBySchoolId(Long schoolId);
}
