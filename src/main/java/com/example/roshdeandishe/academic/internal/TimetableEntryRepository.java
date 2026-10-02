package com.example.roshdeandishe.academic.internal;

import com.example.roshdeandishe.academic.TimetableDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface TimetableEntryRepository extends JpaRepository<TimetableEntryEntity, Long> {

    Optional<TimetableEntryEntity> findByClassIdAndWeekdayAndPeriodId(Long classId, TimetableDay weekday, Long periodId);

    Optional<TimetableEntryEntity> findByTeacherUserIdAndWeekdayAndPeriodId(
            Long teacherUserId, TimetableDay weekday, Long periodId);

    boolean existsByPeriodId(Long periodId);

    boolean existsByActivityId(Long activityId);

    List<TimetableEntryEntity> findByClassIdInAndPeriodIdIn(Collection<Long> classIds, Collection<Long> periodIds);

    List<TimetableEntryEntity> findBySchoolIdAndTeacherUserIdAndPeriodIdIn(
            Long schoolId, Long teacherUserId, Collection<Long> periodIds);
}
