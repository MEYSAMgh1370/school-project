package com.example.roshdeandishe.school.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface BotRoleRepository extends JpaRepository<BotRoleEntity, BotRoleEntity.Key> {

    List<BotRoleEntity> findBySchoolId(Long schoolId);
}
