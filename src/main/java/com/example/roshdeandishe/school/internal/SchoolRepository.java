package com.example.roshdeandishe.school.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface SchoolRepository extends JpaRepository<SchoolEntity, Long> {

    Optional<SchoolEntity> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
