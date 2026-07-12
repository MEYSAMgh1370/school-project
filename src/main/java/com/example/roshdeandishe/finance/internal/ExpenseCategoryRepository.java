package com.example.roshdeandishe.finance.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategoryEntity, Long> {

    List<ExpenseCategoryEntity> findBySchoolIdOrderByNameAsc(Long schoolId);

    Optional<ExpenseCategoryEntity> findBySchoolIdAndName(Long schoolId, String name);
}
