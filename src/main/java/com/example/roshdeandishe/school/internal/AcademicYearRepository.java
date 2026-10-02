package com.example.roshdeandishe.school.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

interface AcademicYearRepository extends JpaRepository<AcademicYearEntity, Long> {

    List<AcademicYearEntity> findAllByOrderByStartYearAsc();

    /**
     * idempotent و امن در برابر چند instanceِ هم‌زمان (code و start_year یکتا هستند):
     * ۱ اگر ساخته شد، ۰ اگر از قبل بود. تراکنش اینجا است نه روی
     * AcademicYearProvisioner: آن کلاس خودش (ApplicationRunner/@Scheduled) متدش را
     * صدا می‌زند و @Transactional در self-invocation اعمال نمی‌شد (درجِ بدون
     * تراکنش: TransactionRequiredException).
     */
    @Transactional
    @Modifying
    @Query(value = "insert into school.academic_years (code, start_year) values (:code, :startYear) "
            + "on conflict do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("code") String code, @Param("startYear") int startYear);
}
