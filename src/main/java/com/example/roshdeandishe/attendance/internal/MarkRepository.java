package com.example.roshdeandishe.attendance.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

interface MarkRepository extends JpaRepository<MarkEntity, Long> {

    List<MarkEntity> findByClassIdAndDay(Long classId, LocalDate day);

    List<MarkEntity> findBySchoolIdAndDay(Long schoolId, LocalDate day);

    List<MarkEntity> findByStudentIdAndDay(Long studentId, LocalDate day);

    List<MarkEntity> findByStudentIdInAndDayBetween(java.util.Collection<Long> studentIds, LocalDate from,
                                                    LocalDate to);

    List<MarkEntity> findBySchoolIdAndDayBetween(Long schoolId, LocalDate from, LocalDate to);

    /**
     * حذفِ فوریِ مُهرهای یک (کلاس، روز) پیش از نوشتنِ مُهرهای تازه: حذفِ مشتق‌شده (loadAndRemove) در flush بعد از
     * insertهای همان تراکنش اجرا می‌شود و به ایندکس یکتای (کلاس، روز، دانش‌آموز) می‌خورد.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from MarkEntity m where m.classId = :classId and m.day = :day")
    int deleteByClassAndDay(@Param("classId") Long classId, @Param("day") LocalDate day);
}
