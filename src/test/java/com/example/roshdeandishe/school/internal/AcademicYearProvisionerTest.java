package com.example.roshdeandishe.school.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ساخت خودکارِ سال تحصیلی از روی تقویمِ شمسی و تاریخِ کانفیگ (۱ مرداد) بدون
 * Spring و Docker: «امروز» پارامتر است، پس مرز دقیق (۳۱ تیر در برابر ۱ مرداد) و
 * جاماندن چند سال قابل‌آزمون است.
 */
class AcademicYearProvisionerTest {

    private AcademicYearRepository repository;
    private AcademicYearProvisioner provisioner;

    @BeforeEach
    void setUp() {
        repository = mock(AcademicYearRepository.class);
        provisioner = new AcademicYearProvisioner(repository, 5, 1, "Asia/Tehran");
        when(repository.insertIfAbsent(anyString(), anyInt())).thenReturn(1);
    }

    private static AcademicYearEntity year(int startYear) {
        AcademicYearEntity year = new AcademicYearEntity();
        ReflectionTestUtils.setField(year, "startYear", startYear);
        ReflectionTestUtils.setField(year, "code", startYear + "-" + (startYear + 1));
        return year;
    }

    @Test
    void onTheFirstOfMordadTheNewAcademicYearOfThatSolarYearIsCreated() {
        when(repository.findAllByOrderByStartYearAsc()).thenReturn(List.of(year(1404)));

        int created = provisioner.ensureAcademicYears(LocalDate.of(2026, 7, 23)); // 1405/05/01

        assertThat(created).isEqualTo(1);
        verify(repository).insertIfAbsent("1405-1406", 1405);
    }

    @Test
    void theDayBeforeNothingIsCreatedBecauseTheCurrentAcademicYearIsStillLastYears() {
        when(repository.findAllByOrderByStartYearAsc()).thenReturn(List.of(year(1404)));

        int created = provisioner.ensureAcademicYears(LocalDate.of(2026, 7, 22)); // 1405/04/31

        assertThat(created).isZero();
        verify(repository, never()).insertIfAbsent(anyString(), anyInt());
    }

    @Test
    void anExistingYearIsNotCreatedAgain() {
        when(repository.findAllByOrderByStartYearAsc()).thenReturn(List.of(year(1404), year(1405)));

        assertThat(provisioner.ensureAcademicYears(LocalDate.of(2026, 10, 2))).isZero();
        verify(repository, never()).insertIfAbsent(anyString(), anyInt());
    }

    @Test
    void yearsMissedWhileTheApplicationWasDownAreFilledOneByOneWithoutGaps() {
        when(repository.findAllByOrderByStartYearAsc()).thenReturn(List.of(year(1404)));

        int created = provisioner.ensureAcademicYears(LocalDate.of(2028, 8, 1)); // 1407/05/10

        assertThat(created).isEqualTo(3);
        verify(repository).insertIfAbsent("1405-1406", 1405);
        verify(repository).insertIfAbsent("1406-1407", 1406);
        verify(repository).insertIfAbsent("1407-1408", 1407);
    }

    @Test
    void withAnEmptyListOnlyTheCurrentAcademicYearIsCreated() {
        when(repository.findAllByOrderByStartYearAsc()).thenReturn(List.of());

        int created = provisioner.ensureAcademicYears(LocalDate.of(2026, 10, 2));

        assertThat(created).isEqualTo(1);
        verify(repository).insertIfAbsent("1405-1406", 1405);
        verify(repository, never()).insertIfAbsent(ArgumentMatchers.eq("1404-1405"), anyInt());
    }

    @Test
    void aRowAlreadyInsertedByAnotherInstanceIsNotCountedAsCreated() {
        when(repository.findAllByOrderByStartYearAsc()).thenReturn(List.of(year(1404)));
        when(repository.insertIfAbsent("1405-1406", 1405)).thenReturn(0);

        assertThat(provisioner.ensureAcademicYears(LocalDate.of(2026, 10, 2))).isZero();
    }

    @Test
    void theStartDateComesFromConfigNotFromTheCode() {
        // یک مدرسه‌ی نمونه با شروعِ ۱ مهر: ۳۱ شهریور هنوز سالِ قبل، ۱ مهر سالِ تازه
        AcademicYearProvisioner mehr = new AcademicYearProvisioner(repository, 7, 1, "Asia/Tehran");

        assertThat(mehr.currentAcademicStartYear(LocalDate.of(2026, 9, 22))).isEqualTo(1404); // 1405/06/31
        assertThat(mehr.currentAcademicStartYear(LocalDate.of(2026, 9, 23))).isEqualTo(1405); // 1405/07/01
    }

    @Test
    void aStartDateThatDoesNotExistInTheCalendarFailsAtStartup() {
        assertThatThrownBy(() -> new AcademicYearProvisioner(repository, 13, 1, "Asia/Tehran"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AcademicYearProvisioner(repository, 5, 32, "Asia/Tehran"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AcademicYearProvisioner(repository, 7, 31, "Asia/Tehran"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AcademicYearProvisioner(repository, 12, 30, "Asia/Tehran"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AcademicYearProvisioner(repository, 0, 1, "Asia/Tehran"))
                .isInstanceOf(IllegalStateException.class);
    }
}
