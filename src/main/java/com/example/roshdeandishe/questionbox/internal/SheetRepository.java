package com.example.roshdeandishe.questionbox.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface SheetRepository extends JpaRepository<SheetEntity, Long> {

    Optional<SheetEntity> findByIdAndSchoolId(Long id, Long schoolId);

    List<SheetEntity> findBySchoolIdOrderByIdDesc(Long schoolId);

    boolean existsByHeaderId(Long headerId);
}
