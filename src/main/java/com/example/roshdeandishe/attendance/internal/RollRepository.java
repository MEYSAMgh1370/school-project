package com.example.roshdeandishe.attendance.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

interface RollRepository extends JpaRepository<RollEntity, Long> {

    Optional<RollEntity> findByClassIdAndDay(Long classId, LocalDate day);

    List<RollEntity> findBySchoolIdAndDay(Long schoolId, LocalDate day);

    List<RollEntity> findBySchoolIdAndDayBetween(Long schoolId, LocalDate from, LocalDate to);

    /**
     * قفلِ مشورتیِ سطحِ تراکنش برای یک (کلاس، روز): دو ذخیره‌ی هم‌زمان پشت سر هم اجرا می‌شوند، پس دومی ردیفِ
     * اولی را می‌بیند و جایگزین می‌کند به‌جای اینکه هر دو insert کنند و یکی به ایندکس یکتا بخورد (همان الگوی
     * {@code TimetableEntryRepository.lockCell}). با پایانِ تراکنش آزاد می‌شود.
     */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(:namespace, :key)) as locked", nativeQuery = true)
    int lockClassDay(int namespace, int key);
}
