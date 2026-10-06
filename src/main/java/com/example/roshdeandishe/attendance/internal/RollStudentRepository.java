package com.example.roshdeandishe.attendance.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

interface RollStudentRepository extends JpaRepository<RollStudentEntity, RollStudentEntity.Key> {

    List<RollStudentEntity> findByClassIdAndDay(Long classId, LocalDate day);

    List<RollStudentEntity> findBySchoolIdAndDay(Long schoolId, LocalDate day);

    List<RollStudentEntity> findBySchoolIdAndDayBetween(Long schoolId, LocalDate from, LocalDate to);

    /** حذفِ فوریِ فهرستِ یک (کلاس، روز) پیش از نوشتنِ فهرستِ تازه (همان دلیلِ {@code MarkRepository.deleteByClassAndDay}). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RollStudentEntity r where r.classId = :classId and r.day = :day")
    int deleteByClassAndDay(@Param("classId") Long classId, @Param("day") LocalDate day);
}
