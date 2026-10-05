package com.example.roshdeandishe.school.internal;

import com.example.roshdeandishe.school.SchoolFacade.CalendarKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

interface CalendarEntryRepository extends JpaRepository<CalendarEntryEntity, Long> {

    List<CalendarEntryEntity> findBySchoolIdAndDayBetweenOrderByDayAscIdAsc(Long schoolId, LocalDate from, LocalDate to);

    Optional<CalendarEntryEntity> findByIdAndSchoolId(Long id, Long schoolId);

    boolean existsBySchoolIdAndDayAndKind(Long schoolId, LocalDate day, CalendarKind kind);
}
