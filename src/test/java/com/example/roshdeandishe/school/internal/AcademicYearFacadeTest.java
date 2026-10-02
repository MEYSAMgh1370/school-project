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
    private SchoolFacadeImpl facade;
    private SchoolEntity school;

    @BeforeEach
    void setUp() {
        schools = mock(SchoolRepository.class);
        years = mock(AcademicYearRepository.class);
        facade = new SchoolFacadeImpl(schools, years);
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
    void aSchoolWithoutAChosenYearHasNoCurrentYear() {
        assertThat(facade.findCurrentAcademicYear(SCHOOL)).isEmpty();
        verify(years, never()).findById(any());
    }

    @Test
    void choosingAnOpenYearStoresItAsTheCurrentYear() {
        when(years.findById(2L)).thenReturn(Optional.of(year(2L, "1405-1406", 1405)));

        facade.setCurrentAcademicYear(SCHOOL, new AcademicYearId(2L));

        assertThat(school.getCurrentAcademicYearId()).isEqualTo(2L);
        verify(schools).save(school);
        assertThat(facade.findCurrentAcademicYear(SCHOOL)).get()
                .extracting(AcademicYearInfo::code).isEqualTo("1405-1406");
    }

    @Test
    void aYearNotInTheListYetIsRejectedAndNothingChanges() {
        school.setCurrentAcademicYearId(1L);
        when(years.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.setCurrentAcademicYear(SCHOOL, new AcademicYearId(3L)))
                .isInstanceOfSatisfying(SchoolException.class, e ->
                        assertThat(e.error()).isEqualTo(new SchoolError.AcademicYearNotFound(3L)));
        assertThat(school.getCurrentAcademicYearId()).isEqualTo(1L);
        verify(schools, never()).save(any());
    }

    @Test
    void anUnknownYearOrSchoolIsRejected() {
        when(years.findById(99L)).thenReturn(Optional.empty());
        when(schools.findById(6L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.setCurrentAcademicYear(SCHOOL, new AcademicYearId(99L)))
                .isInstanceOfSatisfying(SchoolException.class, e ->
                        assertThat(e.error()).isEqualTo(new SchoolError.AcademicYearNotFound(99L)));
        assertThatThrownBy(() -> facade.setCurrentAcademicYear(new SchoolId(6L), new AcademicYearId(1L)))
                .isInstanceOfSatisfying(SchoolException.class, e ->
                        assertThat(e.error()).isEqualTo(new SchoolError.SchoolNotFound(6L)));
        verify(schools, never()).save(any());
    }
}
