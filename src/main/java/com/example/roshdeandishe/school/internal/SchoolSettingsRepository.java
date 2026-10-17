package com.example.roshdeandishe.school.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SchoolSettingsRepository extends JpaRepository<SchoolSettingsEntity, Long> {

    /**
     * ساختِ ردیفِ تنظیمات از ستون‌های قدیمیِ school.schools اگر نباشد؛ امنِ هم‌زمانی
     * (on conflict do nothing). اگر مدرسه نباشد چیزی درج نمی‌شود (۰).
     */
    @Modifying
    @Query(value = """
            insert into school.school_settings (school_id, installments_enabled, auto_promotion, bot_enabled)
            select id, installments_enabled, auto_promotion, true from school.schools where id = :schoolId
            on conflict do nothing
            """, nativeQuery = true)
    int createIfMissing(@Param("schoolId") Long schoolId);
}
