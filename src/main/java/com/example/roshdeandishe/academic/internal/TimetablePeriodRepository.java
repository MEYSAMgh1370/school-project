package com.example.roshdeandishe.academic.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

interface TimetablePeriodRepository extends JpaRepository<TimetablePeriodEntity, Long> {

    List<TimetablePeriodEntity> findBySchoolIdAndAcademicYearOrderByStartTimeAscPositionAsc(
            Long schoolId, String academicYear);

    List<TimetablePeriodEntity> findByIdIn(Collection<Long> ids);

    /**
     * قفلِ مشورتیِ سطحِ تراکنش برای یک (مدرسه، سال): ساخت/ویرایشِ زنگ‌ها پشت سر هم اجرا
     * می‌شوند تا دو مدیرِ هم‌زمان هر دو از بررسیِ هم‌پوشانی (و شماره‌ی ترتیب) رد نشوند - ساختِ
     * ایندکسِ بازه‌ای (exclusion constraint) افزونه‌ی btree_gist می‌خواهد. با پایانِ تراکنش آزاد
     * می‌شود. ستونِ برگشتی فقط برای این است که Spring Data نتیجه‌ی void را نخواند.
     */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(:namespace, :key)) as locked", nativeQuery = true)
    int lockSchoolYear(int namespace, int key);

    /** قفلِ یک زنگ: حذفِ زنگ و ثبتِ خانه برای آن پشت سر هم اجرا می‌شوند (قفلِ خانه همیشه بعد از این گرفته می‌شود). */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(:namespace, :key)) as locked", nativeQuery = true)
    int lockPeriod(int namespace, int key);

    @Query("select coalesce(max(p.position), 0) from TimetablePeriodEntity p "
            + "where p.schoolId = :schoolId and p.academicYear = :academicYear")
    int maxPosition(Long schoolId, String academicYear);
}
