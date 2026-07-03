package com.example.roshdeandishe.iam.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface PermissionRepository extends JpaRepository<PermissionEntity, Long> {

    Optional<PermissionEntity> findByCode(String code);
}
