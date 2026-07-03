package com.example.roshdeandishe.iam.internal;

import org.springframework.data.jpa.repository.JpaRepository;

interface RolePermissionRepository extends JpaRepository<RolePermissionEntity, Long> {

    boolean existsByRoleIdAndPermissionId(Long roleId, Long permissionId);
}
