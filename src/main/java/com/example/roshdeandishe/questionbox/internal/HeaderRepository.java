package com.example.roshdeandishe.questionbox.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface HeaderRepository extends JpaRepository<HeaderEntity, Long> {

    Optional<HeaderEntity> findByIdAndSchoolId(Long id, Long schoolId);

    List<HeaderEntity> findBySchoolIdOrderByIdDesc(Long schoolId);
}
