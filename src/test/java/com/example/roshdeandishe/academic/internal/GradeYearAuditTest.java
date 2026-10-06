package com.example.roshdeandishe.academic.internal;

import com.example.roshdeandishe.school.SchoolFacade;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/** گزارشِ سال‌های خارج از فهرست فقط می‌شمارد؛ هیچ نمره‌ای را تغییر یا حذف نمی‌کند. */
class GradeYearAuditTest {

    @Test
    void reportsOnlyTheSchoolYearPairsThatMatchNoListedYearAndTouchesNothing() {
        GradeRepository grades = mock(GradeRepository.class);
        SchoolFacade school = mock(SchoolFacade.class);
        when(school.listAcademicYears()).thenReturn(List.of(
                new SchoolFacade.AcademicYearInfo(new SchoolFacade.AcademicYearId(1L), "1404-1405", 1404,
                        java.time.LocalDate.of(2025, 9, 23), java.time.LocalDate.of(2026, 6, 21))));
        when(grades.countBySchoolAndYear()).thenReturn(List.of(
                new Object[]{5L, "1404-1405", 10L},
                new Object[]{5L, "1405", 2L},
                new Object[]{6L, "۱۴۰۴-۱۴۰۵", 1L}));

        int unmatched = new GradeYearAudit(grades, school).report();

        assertThat(unmatched).isEqualTo(2);
        verify(grades, never()).save(any());
        verify(grades, never()).delete(any());
    }

    @Test
    void reportsNothingWhenEveryYearIsListed() {
        GradeRepository grades = mock(GradeRepository.class);
        SchoolFacade school = mock(SchoolFacade.class);
        when(school.listAcademicYears()).thenReturn(List.of(
                new SchoolFacade.AcademicYearInfo(new SchoolFacade.AcademicYearId(1L), "1404-1405", 1404,
                        java.time.LocalDate.of(2025, 9, 23), java.time.LocalDate.of(2026, 6, 21))));
        when(grades.countBySchoolAndYear()).thenReturn(List.<Object[]>of(new Object[]{5L, "1404-1405", 3L}));

        assertThat(new GradeYearAudit(grades, school).report()).isZero();
    }

    @Test
    void aListedYearStoredWithPersianDigitsIsStillReportedAsUnmatched() {
        GradeRepository grades = mock(GradeRepository.class);
        SchoolFacade school = mock(SchoolFacade.class);
        when(school.listAcademicYears()).thenReturn(List.of(
                new SchoolFacade.AcademicYearInfo(new SchoolFacade.AcademicYearId(1L), "1404-1405", 1404,
                        java.time.LocalDate.of(2025, 9, 23), java.time.LocalDate.of(2026, 6, 21))));
        // «۱۴۰۴-۱۴۰۵» و «1404-1405 » سالِ درست‌اند ولی غیرِ canonical؛ جستجوها آن‌ها را پیدا نمی‌کنند
        when(grades.countBySchoolAndYear()).thenReturn(List.of(
                new Object[]{5L, "۱۴۰۴-۱۴۰۵", 2L}, new Object[]{5L, "1404-1405 ", 1L}, new Object[]{5L, "1404-1405", 9L}));

        assertThat(new GradeYearAudit(grades, school).report()).isEqualTo(2);
    }
}
