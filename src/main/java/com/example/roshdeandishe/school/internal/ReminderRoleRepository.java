package com.example.roshdeandishe.school.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface ReminderRoleRepository extends JpaRepository<ReminderRoleEntity, ReminderRoleEntity.Key> {

    List<ReminderRoleEntity> findBySchoolId(Long schoolId);
}
