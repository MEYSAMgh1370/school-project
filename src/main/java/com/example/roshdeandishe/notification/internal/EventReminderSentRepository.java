package com.example.roshdeandishe.notification.internal;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface EventReminderSentRepository extends Repository<EventReminderSentEntity, EventReminderSentEntity.Key> {

    /**
     * ادعای اتمیکِ «این کاربر برای این رویداد»: ۱ اگر ردیف همین‌الان درج شد (باید پیام برود)، ۰ اگر از قبل بود.
     * تراکنشِ کوتاهِ خودش را دارد (مثل AcademicYearRepository.insertIfAbsent) و روی کلیدِ اصلی race-safe است.
     */
    @Transactional
    @Modifying
    @Query(value = "insert into bale.event_reminders_sent (calendar_entry_id, user_id) "
            + "values (:entryId, :userId) on conflict do nothing", nativeQuery = true)
    int claim(@Param("entryId") long entryId, @Param("userId") long userId);
}
