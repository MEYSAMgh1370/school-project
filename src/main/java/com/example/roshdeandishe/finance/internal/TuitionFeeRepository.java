package com.example.roshdeandishe.finance.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface TuitionFeeRepository extends JpaRepository<TuitionFeeEntity, Long> {

    Optional<TuitionFeeEntity> findBySchoolId(Long schoolId);
}
