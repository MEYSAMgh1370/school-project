package com.example.roshdeandishe.school.controller;

import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearId;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearInfo;
import com.example.roshdeandishe.school.SchoolFacade.PlatformHolidayInfo;
import com.example.roshdeandishe.school.dto.request.AddPlatformHolidayRequest;
import com.example.roshdeandishe.school.dto.response.AcademicYearResponse;
import com.example.roshdeandishe.school.dto.response.PlatformHolidayResponse;
import com.example.roshdeandishe.school.shared.SchoolError;
import com.example.roshdeandishe.school.shared.SchoolException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** نگاشت پاسخ و بدنه‌ی تهی در مسیر تعطیلی سراسری، بدون Spring و Docker. */
class PlatformHolidayControllerTest {

    private static final LocalDate DAY = LocalDate.of(2027, 3, 21);

    private SchoolFacade school;
    private PlatformHolidayController controller;

    @BeforeEach
    void setUp() {
        school = mock(SchoolFacade.class);
        controller = new PlatformHolidayController(school);
    }

    @Test
    void yearsAreMappedWithTheDefaultRangeInFacadeOrder() {
        when(school.listAcademicYears()).thenReturn(List.of(
                new AcademicYearInfo(new AcademicYearId(1L), "1404-1405", 1404,
                        LocalDate.of(2025, 9, 23), LocalDate.of(2026, 6, 21)),
                new AcademicYearInfo(new AcademicYearId(2L), "1405-1406", 1405,
                        LocalDate.of(2026, 9, 23), LocalDate.of(2027, 6, 21))));

        List<AcademicYearResponse> years = controller.years();

        assertThat(years).containsExactly(
                new AcademicYearResponse(1L, "1404-1405", LocalDate.of(2025, 9, 23), LocalDate.of(2026, 6, 21)),
                new AcademicYearResponse(2L, "1405-1406", LocalDate.of(2026, 9, 23), LocalDate.of(2027, 6, 21)));
    }

    @Test
    void holidaysAreMappedWithoutTheYearId() {
        when(school.listPlatformHolidays(new AcademicYearId(2L))).thenReturn(List.of(
                new PlatformHolidayInfo(7L, new AcademicYearId(2L), DAY, "نوروز")));

        assertThat(controller.list(2L)).containsExactly(new PlatformHolidayResponse(7L, DAY, "نوروز"));
    }

    @Test
    void addDelegatesAndMapsTheCreatedHoliday() {
        when(school.addPlatformHoliday(new AcademicYearId(2L), DAY, "نوروز")).thenReturn(
                new PlatformHolidayInfo(7L, new AcademicYearId(2L), DAY, "نوروز"));

        PlatformHolidayResponse response = controller.add(2L, new AddPlatformHolidayRequest(DAY, "نوروز"));

        assertThat(response).isEqualTo(new PlatformHolidayResponse(7L, DAY, "نوروز"));
    }

    @Test
    void aNullBodyIsACleanInvalidEntryAndNeverReachesTheFacade() {
        assertThatThrownBy(() -> controller.add(2L, null))
                .isInstanceOfSatisfying(SchoolException.class, e ->
                        assertThat(e.error()).isInstanceOf(SchoolError.InvalidCalendarEntry.class));
        verifyNoInteractions(school);
    }

    @Test
    void removeReturnsNoContent() {
        assertThat(controller.remove(7L).getStatusCode().value()).isEqualTo(204);
        verify(school).removePlatformHoliday(7L);
    }
}
