package com.example.roshdeandishe.academic.internal;

import com.example.roshdeandishe.academic.TimetableDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface TimetableEntryRepository extends JpaRepository<TimetableEntryEntity, Long> {

    Optional<TimetableEntryEntity> findByClassIdAndWeekdayAndPeriodId(Long classId, TimetableDay weekday, Long periodId);

    Optional<TimetableEntryEntity> findByTeacherUserIdAndWeekdayAndPeriodId(
            Long teacherUserId, TimetableDay weekday, Long periodId);

    /**
     * قفلِ مشورتیِ سطحِ تراکنش برای یک خانه (کلاس، روز، زنگ): دو مدیرِ هم‌زمان روی یک خانه‌ی
     * خالی پشت سر هم اجرا می‌شوند، پس دومی ردیفِ اولی را می‌بیند و جایگزینش می‌کند به‌جای اینکه
     * هر دو insert کنند و یکی به ایندکس یکتا بخورد. با پایانِ تراکنش آزاد می‌شود.
     */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(:namespace, :key)) as locked", nativeQuery = true)
    int lockCell(int namespace, int key);

    boolean existsByPeriodId(Long periodId);

    boolean existsByActivityId(Long activityId);

    List<TimetableEntryEntity> findByClassIdInAndPeriodIdIn(Collection<Long> classIds, Collection<Long> periodIds);

    List<TimetableEntryEntity> findBySchoolIdAndTeacherUserIdAndPeriodIdIn(
            Long schoolId, Long teacherUserId, Collection<Long> periodIds);
}
