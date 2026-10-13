package com.example.roshdeandishe.questionbox.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface SheetItemRepository extends JpaRepository<SheetItemEntity, Long> {

    List<SheetItemEntity> findBySectionIdInOrderByPositionAsc(Collection<Long> sectionIds);
}
