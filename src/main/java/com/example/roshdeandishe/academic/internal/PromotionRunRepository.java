package com.example.roshdeandishe.academic.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface PromotionRunRepository extends JpaRepository<PromotionRunEntity, Long> {

    List<PromotionRunEntity> findBySchoolIdOrderByIdDesc(Long schoolId);

    /** آخرین اجرای برنده‌نشده (undo نشده) برای سالِ هدف. */
    Optional<PromotionRunEntity> findFirstBySchoolIdAndToYearAndUndoneAtIsNullOrderByIdDesc(Long schoolId, String toYear);

    boolean existsBySchoolIdAndIdGreaterThanAndUndoneAtIsNull(Long schoolId, Long id);
}
