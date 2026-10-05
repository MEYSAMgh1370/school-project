package com.example.roshdeandishe.school.internal;

import com.example.roshdeandishe.school.SchoolFacade.CalendarKind;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.shared.SchoolError;
import com.example.roshdeandishe.school.shared.SchoolException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * اعتبارسنجیِ تقویم مدرسه بدون Spring و Docker: عنوانِ خالی/بلند، روز یا نوعِ خالی و
 * بازه‌ی وارونه رد می‌شوند و چیزی ذخیره نمی‌شود.
 */
class SchoolCalendarFacadeTest {

    private static final SchoolId SCHOOL = new SchoolId(5L);
    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);

    private SchoolRepository schools;
    private CalendarEntryRepository calendar;
    private SchoolFacadeImpl facade;

    @BeforeEach
    void setUp() {
        schools = mock(SchoolRepository.class);
        calendar = mock(CalendarEntryRepository.class);
        facade = new SchoolFacadeImpl(schools, mock(AcademicYearRepository.class), calendar,
                mock(org.springframework.context.ApplicationEventPublisher.class));
        when(schools.existsById(5L)).thenReturn(true);
    }

    @Test
    void blankOrTooLongTitleIsRejectedAndNothingIsSaved() {
        for (String title : new String[]{null, "", "   ", "x".repeat(129)}) {
            assertThatThrownBy(() -> facade.addCalendarEntry(SCHOOL, DAY, CalendarKind.CLOSED, title))
                    .isInstanceOfSatisfying(SchoolException.class,
                            e -> assertThat(e.error()).isInstanceOf(SchoolError.InvalidCalendarEntry.class));
        }
        verify(calendar, never()).save(any());
    }

    @Test
    void missingDayOrKindIsRejected() {
        assertThatThrownBy(() -> facade.addCalendarEntry(SCHOOL, null, CalendarKind.CLOSED, "نوروز"))
                .isInstanceOf(SchoolException.class);
        assertThatThrownBy(() -> facade.addCalendarEntry(SCHOOL, DAY, null, "نوروز"))
                .isInstanceOf(SchoolException.class);
        verify(calendar, never()).save(any());
    }

    @Test
    void titleOfExactlyTheMaximumLengthIsTrimmedAndAccepted() {
        when(calendar.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var info = facade.addCalendarEntry(SCHOOL, DAY, CalendarKind.EVENT, "  " + "x".repeat(128) + "  ");

        assertThat(info.title()).hasSize(128);
        assertThat(info.kind()).isEqualTo(CalendarKind.EVENT);
    }

    @Test
    void unknownSchoolIsNotFound() {
        assertThatThrownBy(() -> facade.addCalendarEntry(new SchoolId(99L), DAY, CalendarKind.CLOSED, "نوروز"))
                .isInstanceOfSatisfying(SchoolException.class,
                        e -> assertThat(e.error()).isInstanceOf(SchoolError.SchoolNotFound.class));
    }

    @Test
    void invertedOrMissingRangeIsRejected() {
        assertThatThrownBy(() -> facade.listCalendar(SCHOOL, DAY, DAY.minusDays(1)))
                .isInstanceOf(SchoolException.class);
        assertThatThrownBy(() -> facade.listCalendar(SCHOOL, null, DAY))
                .isInstanceOf(SchoolException.class);
    }

    @Test
    void removingAnEntryThatIsNotThisSchoolsIsNotFound() {
        when(calendar.findByIdAndSchoolId(7L, 5L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> facade.removeCalendarEntry(SCHOOL, 7L))
                .isInstanceOfSatisfying(SchoolException.class,
                        e -> assertThat(e.error()).isInstanceOf(SchoolError.CalendarEntryNotFound.class));
        verify(calendar, never()).delete(any());
    }
}
