package com.example.roshdeandishe.school.internal;

import com.example.roshdeandishe.school.SchoolFacade.AcademicYearId;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearInfo;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.shared.SchoolError;
import com.example.roshdeandishe.school.shared.SchoolException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * سال تحصیلی جاری (openspec/changes/academic-year.md، فاز ۰) بدون Spring و
 * Docker: فقط سالِ موجودِ فهرست انتخاب می‌شود، سالِ ناموجود (مثلاً سالِ آینده‌ای
 * که هنوز ساخته نشده) و مدرسه‌ی ناموجود رد می‌شود و چیزی ذخیره نمی‌شود، و
 * «انتخاب‌نشده» خالی است نه حدس.
 */
class AcademicYearFacadeTest {

    private static final SchoolId SCHOOL = new SchoolId(5L);

    private SchoolRepository schools;
    private AcademicYearRepository years;
    private org.springframework.context.ApplicationEventPublisher events;
    private SchoolFacadeImpl facade;
    private SchoolEntity school;

    @BeforeEach
    void setUp() {
        schools = mock(SchoolRepository.class);
        years = mock(AcademicYearRepository.class);
        events = mock(org.springframework.context.ApplicationEventPublisher.class);
        facade = new SchoolFacadeImpl(schools, years, mock(CalendarEntryRepository.class),
                mock(PlatformHolidayRepository.class), mock(SchoolYearRangeRepository.class), events,
                mock(ReminderRoleRepository.class));
        school = new SchoolEntity();
        ReflectionTestUtils.setField(school, "id", 5L);
        when(schools.findById(5L)).thenReturn(Optional.of(school));
    }

    private static AcademicYearEntity year(long id, String code, int startYear) {
        AcademicYearEntity year = new AcademicYearEntity();
        ReflectionTestUtils.setField(year, "id", id);
        ReflectionTestUtils.setField(year, "code", code);
        ReflectionTestUtils.setField(year, "startYear", startYear);
        return year;
    }

    @Test
    void listIsOrderedFromOldestToNewest() {
        when(years.findAllByOrderByStartYearAsc()).thenReturn(List.of(
                year(1L, "1404-1405", 1404), year(2L, "1405-1406", 1405)));

        List<AcademicYearInfo> result = facade.listAcademicYears();

        assertThat(result).extracting(AcademicYearInfo::code).containsExactly("1404-1405", "1405-1406");
    }

    @Test
    void yearRunsFromFirstOfMehrToThirtyFirstOfKhordadOfTheNextYear() {
        when(years.findAllByOrderByStartYearAsc()).thenReturn(List.of(year(2L, "1405-1406", 1405)));

        AcademicYearInfo info = facade.listAcademicYears().get(0);

        assertThat(info.startDate()).isEqualTo(java.time.LocalDate.of(2026, 9, 23));
        assertThat(info.endDate()).isEqualTo(java.time.LocalDate.of(2027, 6, 21));
    }

    @Test
    void aSchoolWithoutAChosenYearHasNoCurrentYear() {
        assertThat(facade.findCurrentAcademicYear(SCHOOL)).isEmpty();
        verify(years, never()).findById(any());
    }

    @Test
    void choosingAYearWritesItThroughACompareAndSetAgainstTheExpectedCurrentYear() {
        when(years.findById(2L)).thenReturn(Optional.of(year(2L, "1405-1406", 1405)));
        when(schools.compareAndSetCurrentAcademicYear(5L, 1L, 2L)).thenReturn(1);

        facade.setCurrentAcademicYear(SCHOOL, new AcademicYearId(2L), new AcademicYearId(1L));

        verify(schools).compareAndSetCurrentAcademicYear(5L, 1L, 2L);
    }

    @Test
    void aSchoolThatHasNotChosenYetIsExpectedAsNull() {
        when(years.findById(2L)).thenReturn(Optional.of(year(2L, "1405-1406", 1405)));
        when(schools.compareAndSetCurrentAcademicYear(5L, null, 2L)).thenReturn(1);

        facade.setCurrentAcademicYear(SCHOOL, new AcademicYearId(2L), null);

        verify(schools).compareAndSetCurrentAcademicYear(5L, null, 2L);
    }

    @Test
    void aStaleExpectationIsRejectedAsAChangeAndTheOlderChoiceIsNotApplied() {
        when(years.findById(2L)).thenReturn(Optional.of(year(2L, "1405-1406", 1405)));
        // سالِ جاری در این فاصله عوض شده: UPDATE هیچ سطری را تغییر نمی‌دهد
        when(schools.compareAndSetCurrentAcademicYear(5L, 1L, 2L)).thenReturn(0);

        assertThatThrownBy(() -> facade.setCurrentAcademicYear(SCHOOL, new AcademicYearId(2L), new AcademicYearId(1L)))
                .isInstanceOfSatisfying(SchoolException.class, e ->
                        assertThat(e.error()).isEqualTo(new SchoolError.AcademicYearChanged(1L)));
    }

    @Test
    void aYearNotInTheListYetIsRejectedBeforeAnyWrite() {
        when(years.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.setCurrentAcademicYear(SCHOOL, new AcademicYearId(3L), null))
                .isInstanceOfSatisfying(SchoolException.class, e ->
                        assertThat(e.error()).isEqualTo(new SchoolError.AcademicYearNotFound(3L)));
        verify(schools, never()).compareAndSetCurrentAcademicYear(any(), any(), any());
    }

    @Test
    void anUnknownSchoolIsRejectedBeforeAnyWrite() {
        when(schools.findById(6L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.setCurrentAcademicYear(new SchoolId(6L), new AcademicYearId(1L), null))
                .isInstanceOfSatisfying(SchoolException.class, e ->
                        assertThat(e.error()).isEqualTo(new SchoolError.SchoolNotFound(6L)));
        verify(schools, never()).compareAndSetCurrentAcademicYear(any(), any(), any());
    }

    @Test
    void changingToAnotherYearPublishesTheChangeWithBothYears() {
        when(years.findById(2L)).thenReturn(Optional.of(year(2L, "1405-1406", 1405)));
        when(years.findById(1L)).thenReturn(Optional.of(year(1L, "1404-1405", 1404)));
        when(schools.compareAndSetCurrentAcademicYear(5L, 1L, 2L)).thenReturn(1);

        facade.setCurrentAcademicYear(SCHOOL, new AcademicYearId(2L), new AcademicYearId(1L));

        verify(events).publishEvent(new com.example.roshdeandishe.school.CurrentAcademicYearChangedEvent(
                5L, "1404-1405", 1404, "1405-1406", 1405));
    }

    @Test
    void theFirstChoiceAndAChoiceOfTheSameYearPublishNothing() {
        when(years.findById(2L)).thenReturn(Optional.of(year(2L, "1405-1406", 1405)));
        when(schools.compareAndSetCurrentAcademicYear(5L, null, 2L)).thenReturn(1);
        when(schools.compareAndSetCurrentAcademicYear(5L, 2L, 2L)).thenReturn(1);

        facade.setCurrentAcademicYear(SCHOOL, new AcademicYearId(2L), null);
        facade.setCurrentAcademicYear(SCHOOL, new AcademicYearId(2L), new AcademicYearId(2L));

        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void aRejectedChangePublishesNothing() {
        when(years.findById(2L)).thenReturn(Optional.of(year(2L, "1405-1406", 1405)));
        when(schools.compareAndSetCurrentAcademicYear(5L, 1L, 2L)).thenReturn(0);

        assertThatThrownBy(() -> facade.setCurrentAcademicYear(SCHOOL, new AcademicYearId(2L), new AcademicYearId(1L)))
                .isInstanceOf(SchoolException.class);

        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void autoPromotionIsOffUntilTheSchoolTurnsItOn() {
        assertThat(facade.isAutoPromotion(SCHOOL)).isFalse();

        facade.setAutoPromotion(SCHOOL, true);

        assertThat(facade.isAutoPromotion(SCHOOL)).isTrue();
        verify(schools).save(school);
    }
}
