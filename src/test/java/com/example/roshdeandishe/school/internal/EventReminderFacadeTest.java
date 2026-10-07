package com.example.roshdeandishe.school.internal;

import com.example.roshdeandishe.school.SchoolFacade.AcademicYearId;
import com.example.roshdeandishe.school.SchoolFacade.CalendarKind;
import com.example.roshdeandishe.school.SchoolFacade.ReminderEvent;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.SchoolFacade.YearlyPlanItem;
import com.example.roshdeandishe.school.SchoolFacade.YearlyPlanSource;
import com.example.roshdeandishe.school.shared.SchoolError;
import com.example.roshdeandishe.school.shared.SchoolException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * یادآوری رویدادهای برنامه‌ی سالانه (openspec/changes/event-reminders.md) بدون Spring و Docker:
 * بازه‌ی زمانِ یادآوری (۰..۳۰)، پنجره‌ی «سررسید»، و قاعده‌ی انتخابِ نقش‌ها (بدون انتخاب = همه).
 */
class EventReminderFacadeTest {

    private static final SchoolId SCHOOL = new SchoolId(5L);
    private static final AcademicYearId YEAR = new AcademicYearId(2L);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private SchoolRepository schools;
    private CalendarEntryRepository calendar;
    private ReminderRoleRepository reminderRoles;
    private SchoolFacadeImpl facade;
    private LocalDate yearStart;

    @BeforeEach
    void setUp() {
        schools = mock(SchoolRepository.class);
        AcademicYearRepository years = mock(AcademicYearRepository.class);
        calendar = mock(CalendarEntryRepository.class);
        reminderRoles = mock(ReminderRoleRepository.class);
        facade = new SchoolFacadeImpl(schools, years, calendar, mock(PlatformHolidayRepository.class),
                mock(SchoolYearRangeRepository.class), mock(org.springframework.context.ApplicationEventPublisher.class),
                reminderRoles);
        when(schools.existsById(5L)).thenReturn(true);
        AcademicYearEntity year = new AcademicYearEntity();
        ReflectionTestUtils.setField(year, "id", 2L);
        ReflectionTestUtils.setField(year, "code", "1405-1406");
        ReflectionTestUtils.setField(year, "startYear", 1405);
        when(years.findById(2L)).thenReturn(Optional.of(year));
        when(calendar.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        yearStart = facade.yearlyPlan(SCHOOL, YEAR).year().startDate();
    }

    private static CalendarEntryEntity event(long id, LocalDate day, Integer lead, CalendarKind kind) {
        CalendarEntryEntity entity = new CalendarEntryEntity(5L, day, kind, "رویداد " + id,
                lead == null ? null : lead.shortValue());
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private void stubCandidates(CalendarEntryEntity... entries) {
        when(calendar.findBySchoolIdAndKindAndRemindDaysBeforeIsNotNullAndDayBetweenOrderByDayAscIdAsc(
                eq(5L), eq(CalendarKind.EVENT), any(), any())).thenReturn(List.of(entries));
    }

    private static void assertInvalidEntry(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(SchoolException.class,
                e -> assertThat(e.error()).isInstanceOf(SchoolError.InvalidCalendarEntry.class));
    }

    private static void assertInvalidSetting(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(SchoolException.class,
                e -> assertThat(e.error()).isInstanceOf(SchoolError.InvalidSetting.class));
    }

    // ---------------------------------------------------------------- زمان یادآوری روی رویداد

    @Test
    void leadTimeBetweenZeroAndThirtyOrNoneIsAcceptedAndReported() {
        YearlyPlanItem none = facade.addYearlyPlanEvent(SCHOOL, YEAR, yearStart, "بدون یادآوری", null);
        YearlyPlanItem sameDay = facade.addYearlyPlanEvent(SCHOOL, YEAR, yearStart, "همان روز", 0);
        YearlyPlanItem max = facade.addYearlyPlanEvent(SCHOOL, YEAR, yearStart, "سی روز", 30);

        assertThat(none.remindDaysBefore()).isNull();
        assertThat(sameDay.remindDaysBefore()).isZero();
        assertThat(max.remindDaysBefore()).isEqualTo(30);
        assertThat(max.source()).isEqualTo(YearlyPlanSource.SCHOOL);

        ArgumentCaptor<CalendarEntryEntity> saved = ArgumentCaptor.forClass(CalendarEntryEntity.class);
        verify(calendar, org.mockito.Mockito.times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(CalendarEntryEntity::getKind)
                .containsOnly(CalendarKind.EVENT);
        assertThat(saved.getAllValues()).extracting(CalendarEntryEntity::getRemindDaysBefore)
                .containsExactly(null, (short) 0, (short) 30);
    }

    @Test
    void leadTimeOutsideZeroToThirtyIsRejectedAndNothingIsSaved() {
        assertInvalidEntry(() -> facade.addYearlyPlanEvent(SCHOOL, YEAR, yearStart, "منفی", -1));
        assertInvalidEntry(() -> facade.addYearlyPlanEvent(SCHOOL, YEAR, yearStart, "زیاد", 31));
        verify(calendar, never()).save(any());
    }

    // ---------------------------------------------------------------- سررسید یادآوری

    @Test
    void dueWindowIncludesTheFirstReminderDayAndTheEventDayItself() {
        stubCandidates(
                event(1, TODAY.plusDays(3), 3, CalendarKind.EVENT),   // امروز = روز - ۳ → سررسید
                event(2, TODAY, 0, CalendarKind.EVENT),               // همان روز
                event(3, TODAY.plusDays(30), 30, CalendarKind.EVENT), // بیشترین پیش‌اطلاع
                event(4, TODAY.plusDays(1), 1, CalendarKind.EVENT));  // فردا

        List<ReminderEvent> due = facade.dueReminderEvents(SCHOOL, TODAY);

        assertThat(due).extracting(ReminderEvent::id).containsExactly(1L, 2L, 3L, 4L);
        assertThat(due.get(0).remindDaysBefore()).isEqualTo(3);
    }

    @Test
    void notYetDuePastOrWithoutLeadTimeOrClosedEventsAreExcluded() {
        stubCandidates(
                event(1, TODAY.plusDays(3), 2, CalendarKind.EVENT),    // پنجره فردا شروع می‌شود
                event(2, TODAY.minusDays(1), 3, CalendarKind.EVENT),   // گذشته
                event(3, TODAY.plusDays(2), null, CalendarKind.EVENT), // بدون زمان یادآوری
                event(4, TODAY.plusDays(1), 5, CalendarKind.CLOSED),   // روز تعطیل
                event(5, TODAY.plusDays(4), 3, CalendarKind.EVENT));   // پنجره: ۱ روز بعد

        assertThat(facade.dueReminderEvents(SCHOOL, TODAY)).isEmpty();
    }

    @Test
    void onlyTheNextThirtyDaysAreQueriedAndAnUnknownSchoolIsRejected() {
        stubCandidates();
        facade.dueReminderEvents(SCHOOL, TODAY);
        verify(calendar).findBySchoolIdAndKindAndRemindDaysBeforeIsNotNullAndDayBetweenOrderByDayAscIdAsc(
                5L, CalendarKind.EVENT, TODAY, TODAY.plusDays(30));

        when(schools.existsById(9L)).thenReturn(false);
        assertThatThrownBy(() -> facade.dueReminderEvents(new SchoolId(9L), TODAY))
                .isInstanceOfSatisfying(SchoolException.class,
                        e -> assertThat(e.error()).isInstanceOf(SchoolError.SchoolNotFound.class));
    }

    // ---------------------------------------------------------------- نقش‌های دریافت‌کننده

    @Test
    void noStoredRolesMeansEveryRole() {
        when(reminderRoles.findBySchoolId(5L)).thenReturn(List.of());

        assertThat(facade.reminderRoleCodes(SCHOOL)).isEmpty();
    }

    @Test
    void storedRolesAreReturnedAsASet() {
        when(reminderRoles.findBySchoolId(5L)).thenReturn(List.of(
                new ReminderRoleEntity(5L, "PARENT"), new ReminderRoleEntity(5L, "STUDENT")));

        assertThat(facade.reminderRoleCodes(SCHOOL)).contains(Set.of("PARENT", "STUDENT"));
    }

    @Test
    void emptyNullOrBlankSelectionsAreRejectedAndNothingIsWritten() {
        assertInvalidSetting(() -> facade.setReminderRoleCodes(SCHOOL, null));
        assertInvalidSetting(() -> facade.setReminderRoleCodes(SCHOOL, Set.of()));
        assertInvalidSetting(() -> facade.setReminderRoleCodes(SCHOOL, Set.of("  ")));
        assertInvalidSetting(() -> facade.setReminderRoleCodes(SCHOOL, Set.of("X".repeat(65))));
        verify(reminderRoles, never()).saveAll(any());
        verify(reminderRoles, never()).deleteAll(any());
    }

    @Test
    void replacingTheSelectionRemovesOldRolesAndAddsOnlyNewOnes() {
        ReminderRoleEntity teacher = new ReminderRoleEntity(5L, "TEACHER");
        ReminderRoleEntity parent = new ReminderRoleEntity(5L, "PARENT");
        when(reminderRoles.findBySchoolId(5L)).thenReturn(List.of(teacher, parent));

        facade.setReminderRoleCodes(SCHOOL, Set.of("PARENT", " STUDENT "));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<ReminderRoleEntity>> removed = ArgumentCaptor.forClass(Iterable.class);
        verify(reminderRoles).deleteAll(removed.capture());
        assertThat(removed.getValue()).containsExactly(teacher);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<ReminderRoleEntity>> added = ArgumentCaptor.forClass(Iterable.class);
        verify(reminderRoles).saveAll(added.capture());
        assertThat(added.getValue()).extracting(ReminderRoleEntity::getRoleCode).containsExactly("STUDENT");
    }
}
