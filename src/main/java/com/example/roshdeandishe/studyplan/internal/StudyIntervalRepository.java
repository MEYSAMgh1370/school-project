package com.example.roshdeandishe.studyplan.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

interface StudyIntervalRepository extends JpaRepository<StudyIntervalEntity, Long> {

    List<StudyIntervalEntity> findBySchoolIdAndStudentIdAndStartTimeBetweenOrderByStartTimeAsc(
            Long schoolId, Long studentId, LocalDateTime from, LocalDateTime to
    );

    void deleteBySchoolIdAndStudentIdAndStartTimeBetween(Long schoolId, Long studentId, LocalDateTime from, LocalDateTime to);
}
