package com.example.roshdeandishe.attendance.internal;

import com.example.roshdeandishe.attendance.ExcuseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface ExcuseRepository extends JpaRepository<ExcuseEntity, Long> {

    Optional<ExcuseEntity> findByStudentIdAndDay(Long studentId, LocalDate day);

    List<ExcuseEntity> findByClassIdAndDay(Long classId, LocalDate day);

    Optional<ExcuseEntity> findByIdAndSchoolId(Long id, Long schoolId);

    List<ExcuseEntity> findByStudentIdInAndDayBetween(Collection<Long> studentIds, LocalDate from, LocalDate to);

    List<ExcuseEntity> findBySchoolIdAndDayBetweenOrderByDayDescIdDesc(Long schoolId, LocalDate from, LocalDate to);

    List<ExcuseEntity> findBySchoolIdAndStatusAndDayBetweenOrderByDayDescIdDesc(
            Long schoolId, ExcuseStatus status, LocalDate from, LocalDate to);
}
