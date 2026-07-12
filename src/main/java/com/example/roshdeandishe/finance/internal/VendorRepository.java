package com.example.roshdeandishe.finance.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface VendorRepository extends JpaRepository<VendorEntity, Long> {

    List<VendorEntity> findBySchoolIdOrderByNameAsc(Long schoolId);
}
