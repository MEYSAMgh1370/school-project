package com.example.roshdeandishe.school.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

interface SchoolRepository extends JpaRepository<SchoolEntity, Long> {

    Optional<SchoolEntity> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /**
     * «اگر هنوز همان است، عوض کن» در یک UPDATE اتمیک: ۱ اگر عوض شد، ۰ اگر سالِ
     * جاریِ مدرسه دیگر {@code expected} نبود. دو PUTِ هم‌زمان (مثلاً A←B←A در
     * مرورگر یا دو مدیر) نمی‌توانند به ترتیبِ معکوس commit شوند و انتخابِ
     * جدیدتر را بی‌صدا برگردانند: دومی روی قفلِ سطر منتظر می‌ماند و predicate را
     * دوباره می‌سنجد. {@code is not distinct from} برای «expected = هنوز انتخاب
     * نشده» (null) هم درست کار می‌کند.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update school.schools set current_academic_year_id = :newYearId "
            + "where id = :schoolId "
            + "and current_academic_year_id is not distinct from cast(:expected as bigint)", nativeQuery = true)
    int compareAndSetCurrentAcademicYear(@Param("schoolId") Long schoolId,
                                         @Param("expected") Long expected,
                                         @Param("newYearId") Long newYearId);
}
