package com.example.roshdeandishe.studyplan.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

interface StudyIntervalRepository extends JpaRepository<StudyIntervalEntity, Long> {

    /**
     * بازه‌های یک هفته: start_time >= start و end_time <= end - همان
     * فیلترهای gte/lte در get_week_intervals موshavere.
     */
    List<StudyIntervalEntity> findByStudentIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqualOrderByStartTimeAscIdAsc(
            Long studentId, LocalDateTime start, LocalDateTime end);

    boolean existsByStudentIdAndStartTimeGreaterThanEqualAndStartTimeLessThan(
            Long studentId, LocalDateTime start, LocalDateTime end);

    void deleteByStudentIdAndStartTimeGreaterThanEqualAndStartTimeLessThan(
            Long studentId, LocalDateTime start, LocalDateTime end);

    /**
     * پورت مستقیم check_time_overlap موshavere:
     * (start <= newStart و end > newStart) یا (start < newEnd و end >= newEnd).
     */
    @Query("""
            select count(i) > 0 from StudyIntervalEntity i
            where i.studentId = :studentId
              and ((i.startTime <= :start and i.endTime > :start)
                or (i.startTime < :end and i.endTime >= :end))
            """)
    boolean overlaps(@Param("studentId") Long studentId,
                     @Param("start") LocalDateTime start,
                     @Param("end") LocalDateTime end);
}
