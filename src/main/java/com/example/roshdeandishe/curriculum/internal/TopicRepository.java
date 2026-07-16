package com.example.roshdeandishe.curriculum.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface TopicRepository extends JpaRepository<TopicEntity, Long> {

    List<TopicEntity> findBySchoolIdOrderBySortOrderAscIdAsc(Long schoolId);
}
