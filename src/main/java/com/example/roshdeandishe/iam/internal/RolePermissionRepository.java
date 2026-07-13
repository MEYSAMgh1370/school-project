package com.example.roshdeandishe.iam.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface RolePermissionRepository extends JpaRepository<RolePermissionEntity, Long> {

    boolean existsByRoleIdAndPermissionId(Long roleId, Long permissionId);

    List<RolePermissionEntity> findByRoleId(Long roleId);
}
