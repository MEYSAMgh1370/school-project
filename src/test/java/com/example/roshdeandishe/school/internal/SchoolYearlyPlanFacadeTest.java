package com.example.roshdeandishe.school.internal;

import com.example.roshdeandishe.school.SchoolFacade.AcademicYearId;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearInfo;
import com.example.roshdeandishe.school.SchoolFacade.CalendarEntryInfo;
import com.example.roshdeandishe.school.SchoolFacade.CalendarKind;
import com.example.roshdeandishe.school.SchoolFacade.PlatformHolidayInfo;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.SchoolFacade.YearlyPlan;
import com.example.roshdeandishe.school.SchoolFacade.YearlyPlanItem;
import com.example.roshdeandishe.school.SchoolFacade.YearlyPlanRange;
import com.example.roshdeandishe.school.SchoolFacade.YearlyPlanSource;
import com.example.roshdeandishe.school.shared.SchoolError;
import com.example.roshdeandishe.school.shared.SchoolException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * برنامه‌ی سالانه و تعطیلی‌های سراسری (openspec/changes/yearly-plan.md، فاز A و B) بدون Spring و
 * Docker: بازه‌ی سال (۱ مهر تا ۳۱ خرداد، هر دو شامل)، اعتبارسنجی عنوان، حذفِ فقط EVENT همین مدرسه،
 * ادغام و ترتیبِ تعطیلی‌های سراسری، و اثر آن‌ها بر isClosedDay.
 */
class SchoolYearlyPlanFacadeTest {

    private static final SchoolId SCHOOL = new SchoolId(5L);
    private static final AcademicYearId YEAR = new AcademicYearId(2L);

    private SchoolRepository schools;
    private AcademicYearRepository years;
    private CalendarEntryRepository calendar;
    private PlatformHolidayRepository holidays;
    private SchoolYearRangeRepository ranges;
    private SchoolFacadeImpl facade;
    private LocalDate first;
    private LocalDate last;

    @BeforeEach
    void setUp() {
        schools = mock(SchoolRepository.class);
        years = mock(AcademicYearRepository.class);
        calendar = mock(CalendarEntryRepository.class);
        holidays = mock(PlatformHolidayRepository.class);
        ranges = mock(SchoolYearRangeRepository.class);
        facade = new SchoolFacadeImpl(schools, years, calendar, holidays, ranges,
                mock(org.springframework.context.ApplicationEventPublisher.class),
                mock(ReminderRoleRepository.class),
                mock(SchoolSettingsRepository.class), mock(BotRoleRepository.class));
        when(schools.existsById(5L)).thenReturn(true);
        AcademicYearEntity year = new AcademicYearEntity();
        ReflectionTestUtils.setField(year, "id", 2L);
        ReflectionTestUtils.setField(year, "code", "1405-1406");
        ReflectionTestUtils.setField(year, "startYear", 1405);
        when(years.findById(2L)).thenReturn(Optional.of(year));
        when(calendar.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(holidays.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AcademicYearInfo info = facade.yearlyPlan(SCHOOL, YEAR).year();
        first = info.startDate();
        last = info.endDate();
    }

    private static CalendarEntryEntity entry(long id, long schoolId, LocalDate day, CalendarKind kind, String title) {
        CalendarEntryEntity entity = new CalendarEntryEntity(schoolId, day, kind, title);
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static PlatformHolidayEntity holiday(long id, LocalDate day, String title) {
        PlatformHolidayEntity entity = new PlatformHolidayEntity(2L, day, title);
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(SchoolException.class,
                        e -> assertThat(e.error()).isInstanceOf(SchoolError.InvalidCalendarEntry.class));
    }

    @Test
    void firstAndLastDayOfTheYearAreAccepted() {
        YearlyPlanItem a = facade.addYearlyPlanEvent(SCHOOL, YEAR, first, "اولین روز", null);
        YearlyPlanItem b = facade.addYearlyPlanEvent(SCHOOL, YEAR, last, "آخرین روز", null);

        assertThat(a.day()).isEqualTo(first);
        assertThat(a.source()).isEqualTo(YearlyPlanSource.SCHOOL);
        assertThat(a.remindDaysBefore()).isNull();
        assertThat(b.day()).isEqualTo(last);
    }

    @Test
    void dayJustOutsideTheYearIsRejectedAndNothingIsSaved() {
        assertInvalid(() -> facade.addYearlyPlanEvent(SCHOOL, YEAR, first.minusDays(1), "زود", null));
        assertInvalid(() -> facade.addYearlyPlanEvent(SCHOOL, YEAR, last.plusDays(1), "دیر", null));
        verify(calendar, never()).save(any());
    }

    @Test
    void blankOrTooLongTitleIsRejected() {
        for (String title : new String[]{null, "", "   ", "x".repeat(129)}) {
            assertInvalid(() -> facade.addYearlyPlanEvent(SCHOOL, YEAR, first, title, null));
        }
        assertThat(facade.addYearlyPlanEvent(SCHOOL, YEAR, first, "  " + "x".repeat(128) + "  ", null).title())
                .hasSize(128);
    }

    @Test
    void missingDayOrYearIsRejected() {
        assertInvalid(() -> facade.addYearlyPlanEvent(SCHOOL, YEAR, null, "رویداد", null));
        assertInvalid(() -> facade.addYearlyPlanEvent(SCHOOL, null, first, "رویداد", null));
        verify(calendar, never()).save(any());
    }

    @Test
    void unknownYearIsNotFound() {
        AcademicYearId unknown = new AcademicYearId(99L);

        assertThatThrownBy(() -> facade.addYearlyPlanEvent(SCHOOL, unknown, first, "رویداد", null))
                .isInstanceOfSatisfying(SchoolException.class,
                        e -> assertThat(e.error()).isInstanceOf(SchoolError.AcademicYearNotFound.class));
        assertThatThrownBy(() -> facade.yearlyPlan(SCHOOL, unknown))
                .isInstanceOfSatisfying(SchoolException.class,
                        e -> assertThat(e.error()).isInstanceOf(SchoolError.AcademicYearNotFound.class));
        verify(calendar, never()).save(any());
    }

    @Test
    void removingAClosedEntryOrAnotherSchoolsEntryIsNotFound() {
        // فقط EVENT ی همین مدرسه پیدا می‌شود؛ CLOSED یا ورودیِ مدرسه‌ی دیگر خالی برمی‌گردد
        when(calendar.findByIdAndSchoolIdAndKind(7L, 5L, CalendarKind.EVENT)).thenReturn(Optional.empty());
        when(calendar.findByIdAndSchoolIdAndKind(8L, 6L, CalendarKind.EVENT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.removeYearlyPlanEvent(SCHOOL, 7L))
                .isInstanceOfSatisfying(SchoolException.class,
                        e -> assertThat(e.error()).isInstanceOf(SchoolError.CalendarEntryNotFound.class));
        assertThatThrownBy(() -> facade.removeYearlyPlanEvent(SCHOOL, 8L))
                .isInstanceOf(SchoolException.class);
        verify(calendar, never()).delete(any());
    }

    @Test
    void removingThisSchoolsEventDeletesIt() {
        CalendarEntryEntity event = entry(9L, 5L, first, CalendarKind.EVENT, "رویداد");
        when(calendar.findByIdAndSchoolIdAndKind(9L, 5L, CalendarKind.EVENT)).thenReturn(Optional.of(event));

        facade.removeYearlyPlanEvent(SCHOOL, 9L);

        verify(calendar).delete(event);
    }

    @Test
    void planMergesSchoolEventsAndPlatformHolidaysInDayOrder() {
        LocalDate d1 = first.plusDays(10);
        LocalDate d2 = first.plusDays(20);
        LocalDate d3 = first.plusDays(30);
        when(calendar.findBySchoolIdAndKindAndDayBetweenOrderByDayAscIdAsc(5L, CalendarKind.EVENT, first, last))
                .thenReturn(List.of(entry(1L, 5L, d1, CalendarKind.EVENT, "رویداد ۱"),
                        entry(2L, 5L, d3, CalendarKind.EVENT, "رویداد ۲")));
        when(holidays.findByAcademicYearIdOrderByDayAscIdAsc(2L))
                .thenReturn(List.of(holiday(10L, d2, "تعطیلی سراسری"), holiday(11L, d3, "تعطیلی هم‌روز")));

        YearlyPlan plan = facade.yearlyPlan(SCHOOL, YEAR);

        assertThat(plan.year().code()).isEqualTo("1405-1406");
        assertThat(plan.items()).extracting(YearlyPlanItem::day).containsExactly(d1, d2, d3, d3);
        assertThat(plan.items()).extracting(YearlyPlanItem::source).containsExactly(
                YearlyPlanSource.SCHOOL, YearlyPlanSource.PLATFORM, YearlyPlanSource.PLATFORM,
                YearlyPlanSource.SCHOOL);
        assertThat(plan.items()).extracting(YearlyPlanItem::id).containsExactly(1L, 10L, 11L, 2L);
    }

    @Test
    void platformHolidayRangeAndTitleAreValidated() {
        PlatformHolidayInfo ok = facade.addPlatformHoliday(YEAR, last, " نوروز ");

        assertThat(ok.title()).isEqualTo("نوروز");
        assertThat(ok.academicYearId()).isEqualTo(YEAR);
        assertInvalid(() -> facade.addPlatformHoliday(YEAR, first.minusDays(1), "نوروز"));
        assertInvalid(() -> facade.addPlatformHoliday(YEAR, last.plusDays(1), "نوروز"));
        assertInvalid(() -> facade.addPlatformHoliday(YEAR, first, "  "));
        assertInvalid(() -> facade.addPlatformHoliday(YEAR, null, "نوروز"));
        assertThatThrownBy(() -> facade.addPlatformHoliday(new AcademicYearId(99L), first, "نوروز"))
                .isInstanceOfSatisfying(SchoolException.class,
                        e -> assertThat(e.error()).isInstanceOf(SchoolError.AcademicYearNotFound.class));
    }

    @Test
    void removingAnUnknownPlatformHolidayIsNotFound() {
        when(holidays.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.removePlatformHoliday(3L))
                .isInstanceOfSatisfying(SchoolException.class,
                        e -> assertThat(e.error()).isInstanceOf(SchoolError.PlatformHolidayNotFound.class));
        verify(holidays, never()).delete(any());
    }

    @Test
    void removingAPlatformHolidayDeletesIt() {
        PlatformHolidayEntity holiday = holiday(3L, first, "تعطیلی");
        when(holidays.findById(3L)).thenReturn(Optional.of(holiday));

        facade.removePlatformHoliday(3L);

        verify(holidays).delete(holiday);
    }

    @Test
    void listPlatformHolidaysMapsTheYearsRows() {
        when(holidays.findByAcademicYearIdOrderByDayAscIdAsc(2L)).thenReturn(List.of(holiday(3L, first, "تعطیلی")));

        assertThat(facade.listPlatformHolidays(YEAR))
                .containsExactly(new PlatformHolidayInfo(3L, YEAR, first, "تعطیلی"));
    }

    private static void assertInvalidRange(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(SchoolException.class,
                        e -> assertThat(e.error()).isInstanceOf(SchoolError.InvalidYearRange.class));
    }

    private void stubRange(LocalDate start, LocalDate end) {
        when(ranges.findBySchoolIdAndAcademicYearId(5L, 2L))
                .thenReturn(Optional.of(new SchoolYearRangeEntity(5L, 2L, start, end)));
    }

    @Test
    void extendingStartAndEndIsAcceptedAndSaved() {
        YearlyPlanRange earlier = facade.setYearlyPlanRange(SCHOOL, YEAR, first.minusDays(10), last);
        YearlyPlanRange later = facade.setYearlyPlanRange(SCHOOL, YEAR, first, last.plusDays(5));
        YearlyPlanRange both = facade.setYearlyPlanRange(SCHOOL, YEAR, first.minusDays(3), last.plusDays(3));

        assertThat(earlier).isEqualTo(new YearlyPlanRange(first.minusDays(10), last, true));
        assertThat(later).isEqualTo(new YearlyPlanRange(first, last.plusDays(5), true));
        assertThat(both).isEqualTo(new YearlyPlanRange(first.minusDays(3), last.plusDays(3), true));
        verify(ranges, times(3)).save(any());
    }

    @Test
    void extensionIsCappedAtOneJalaliMonthOnEachSide() {
        // پیش‌فرض ۱ مهر ۱۴۰۵ تا ۳۱ خرداد ۱۴۰۶ ⇒ سقف: ۱ شهریور ۱۴۰۵ تا ۳۱ تیر ۱۴۰۶
        LocalDate earliest = SolarCalendar.toGregorian(1405, 6, 1);
        LocalDate latest = SolarCalendar.toGregorian(1406, 4, 31);

        assertThat(facade.setYearlyPlanRange(SCHOOL, YEAR, earliest, latest))
                .isEqualTo(new YearlyPlanRange(earliest, latest, true));

        assertInvalidRange(() -> facade.setYearlyPlanRange(SCHOOL, YEAR, earliest.minusDays(1), last));
        assertInvalidRange(() -> facade.setYearlyPlanRange(SCHOOL, YEAR, first, latest.plusDays(1)));
        assertThatThrownBy(() -> facade.setYearlyPlanRange(SCHOOL, YEAR, earliest.minusDays(1), latest.plusDays(1)))
                .hasMessageContaining("یک ماه");
        // فقط یک بار ذخیره شد؛ درخواست‌های بالاتر از سقف چیزی ننوشتند
        verify(ranges, times(1)).save(any());
    }

    @Test
    void narrowingEitherSideIsRejectedWithTheMinimumInTheMessage() {
        assertInvalidRange(() -> facade.setYearlyPlanRange(SCHOOL, YEAR, first.plusDays(1), last));
        assertInvalidRange(() -> facade.setYearlyPlanRange(SCHOOL, YEAR, first, last.minusDays(1)));
        assertThatThrownBy(() -> facade.setYearlyPlanRange(SCHOOL, YEAR, first.plusDays(1), last))
                .hasMessageContaining("گسترش").hasMessageContaining("1405-1406");
        verify(ranges, never()).save(any());
        verify(ranges, never()).delete(any());
    }

    @Test
    void nullDatesOrEndBeforeStartAreRejected() {
        assertInvalidRange(() -> facade.setYearlyPlanRange(SCHOOL, YEAR, null, last));
        assertInvalidRange(() -> facade.setYearlyPlanRange(SCHOOL, YEAR, first, null));
        assertInvalidRange(() -> facade.setYearlyPlanRange(SCHOOL, YEAR, last.plusDays(2), last.plusDays(1)));
        verify(ranges, never()).save(any());
    }

    @Test
    void settingTheDefaultDatesAgainClearsTheOverride() {
        SchoolYearRangeEntity override = new SchoolYearRangeEntity(5L, 2L, first.minusDays(5), last);
        when(ranges.findBySchoolIdAndAcademicYearId(5L, 2L)).thenReturn(Optional.of(override));

        YearlyPlanRange range = facade.setYearlyPlanRange(SCHOOL, YEAR, first, last);

        assertThat(range).isEqualTo(new YearlyPlanRange(first, last, false));
        verify(ranges).delete(override);
        verify(ranges, never()).save(any());
    }

    @Test
    void settingTheDefaultDatesWithoutAnOverrideDeletesNothing() {
        YearlyPlanRange range = facade.setYearlyPlanRange(SCHOOL, YEAR, first, last);

        assertThat(range.custom()).isFalse();
        verify(ranges, never()).delete(any());
    }

    @Test
    void anExistingOverrideIsUpdatedInPlace() {
        SchoolYearRangeEntity override = new SchoolYearRangeEntity(5L, 2L, first.minusDays(5), last);
        when(ranges.findBySchoolIdAndAcademicYearId(5L, 2L)).thenReturn(Optional.of(override));

        YearlyPlanRange range = facade.setYearlyPlanRange(SCHOOL, YEAR, first.minusDays(9), last.plusDays(2));

        assertThat(range.custom()).isTrue();
        assertThat(override.getStartDate()).isEqualTo(first.minusDays(9));
        assertThat(override.getEndDate()).isEqualTo(last.plusDays(2));
        verify(ranges).save(override);
    }

    @Test
    void settingTheRangeForUnknownYearOrSchoolIsNotFound() {
        assertThatThrownBy(() -> facade.setYearlyPlanRange(SCHOOL, new AcademicYearId(99L), first, last))
                .isInstanceOfSatisfying(SchoolException.class,
                        e -> assertThat(e.error()).isInstanceOf(SchoolError.AcademicYearNotFound.class));
        assertThatThrownBy(() -> facade.setYearlyPlanRange(new SchoolId(404L), YEAR, first, last))
                .isInstanceOfSatisfying(SchoolException.class,
                        e -> assertThat(e.error()).isInstanceOf(SchoolError.SchoolNotFound.class));
        verify(ranges, never()).save(any());
    }

    @Test
    void eventsOnTheExtendedDaysAreAcceptedButJustOutsideTheExtendedRangeIsRejected() {
        stubRange(first.minusDays(10), last.plusDays(10));

        assertThat(facade.addYearlyPlanEvent(SCHOOL, YEAR, first.minusDays(10), "پیش از سال", null).day())
                .isEqualTo(first.minusDays(10));
        assertThat(facade.addYearlyPlanEvent(SCHOOL, YEAR, last.plusDays(10), "پس از سال", null).day())
                .isEqualTo(last.plusDays(10));
        assertInvalid(() -> facade.addYearlyPlanEvent(SCHOOL, YEAR, first.minusDays(11), "زود", null));
        assertInvalid(() -> facade.addYearlyPlanEvent(SCHOOL, YEAR, last.plusDays(11), "دیر", null));
        verify(calendar, times(2)).save(any());
    }

    @Test
    void platformHolidaysAreStillValidatedAgainstTheDefaultRange() {
        stubRange(first.minusDays(10), last.plusDays(10));

        assertInvalid(() -> facade.addPlatformHoliday(YEAR, first.minusDays(1), "تعطیلی"));
        assertInvalid(() -> facade.addPlatformHoliday(YEAR, last.plusDays(1), "تعطیلی"));
        assertThat(facade.addPlatformHoliday(YEAR, last, "تعطیلی").day()).isEqualTo(last);
    }

    @Test
    void listingUsesTheEffectiveRangeAndReportsIt() {
        LocalDate extendedStart = first.minusDays(10);
        LocalDate extendedEnd = last.plusDays(10);
        stubRange(extendedStart, extendedEnd);
        LocalDate early = first.minusDays(4);
        when(calendar.findBySchoolIdAndKindAndDayBetweenOrderByDayAscIdAsc(
                5L, CalendarKind.EVENT, extendedStart, extendedEnd))
                .thenReturn(List.of(entry(1L, 5L, early, CalendarKind.EVENT, "پیش از مهر")));

        YearlyPlan plan = facade.yearlyPlan(SCHOOL, YEAR);

        assertThat(plan.range()).isEqualTo(new YearlyPlanRange(extendedStart, extendedEnd, true));
        assertThat(plan.year().startDate()).isEqualTo(first);
        assertThat(plan.items()).extracting(YearlyPlanItem::day).containsExactly(early);
    }

    @Test
    void listingWithoutAnOverrideReportsTheDefaultRange() {
        assertThat(facade.yearlyPlan(SCHOOL, YEAR).range()).isEqualTo(new YearlyPlanRange(first, last, false));
    }

    @Test
    void platformHolidayClosesTheDayEventAloneDoesNot() {
        LocalDate day = first.plusDays(3);

        // فقط EVENT: هیچ ردیف CLOSED یا تعطیلی سراسری نیست
        assertThat(facade.isClosedDay(SCHOOL, day)).isFalse();

        when(calendar.existsBySchoolIdAndDayAndKind(5L, day, CalendarKind.CLOSED)).thenReturn(true);
        assertThat(facade.isClosedDay(SCHOOL, day)).isTrue();

        LocalDate other = first.plusDays(4);
        assertThat(facade.isClosedDay(SCHOOL, other)).isFalse();
        when(holidays.existsByDay(other)).thenReturn(true);
        assertThat(facade.isClosedDay(SCHOOL, other)).isTrue();
        verify(calendar, never()).existsBySchoolIdAndDayAndKind(eq(5L), eq(other), eq(CalendarKind.EVENT));
    }
}
