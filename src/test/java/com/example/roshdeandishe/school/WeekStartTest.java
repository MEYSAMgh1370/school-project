package com.example.roshdeandishe.school;

import com.example.roshdeandishe.school.SchoolFacade.WeekStart;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * قاعده‌ی هفته‌ی مدرسه (W9): SAT = شنبه تا چهارشنبه، MON = دوشنبه تا جمعه.
 */
class WeekStartTest {

    @Test
    void saturdayWeekIsTheIranianWeek() {
        assertThat(WeekStart.SATURDAY.firstDay()).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(WeekStart.SATURDAY.schoolDays()).containsExactly(
                DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY);
    }

    @Test
    void mondayWeekRunsMondayToFriday() {
        assertThat(WeekStart.MONDAY.firstDay()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(WeekStart.MONDAY.schoolDays()).containsExactly(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
    }

    @Test
    void weekStartOfFindsTheMostRecentFirstDayForEveryWeekday() {
        // 2026-10-03 شنبه است
        LocalDate saturday = LocalDate.of(2026, 10, 3);
        for (int i = 0; i < 7; i++) {
            LocalDate date = saturday.plusDays(i);
            assertThat(WeekStart.SATURDAY.weekStartOf(date)).isEqualTo(saturday);
        }
        assertThat(WeekStart.SATURDAY.weekStartOf(saturday.plusDays(7))).isEqualTo(saturday.plusDays(7));

        LocalDate monday = LocalDate.of(2026, 10, 5);
        for (int i = 0; i < 7; i++) {
            assertThat(WeekStart.MONDAY.weekStartOf(monday.plusDays(i))).isEqualTo(monday);
        }
        // شنبه و یکشنبه به دوشنبه‌ی قبل برمی‌گردند، نه بعد
        assertThat(WeekStart.MONDAY.weekStartOf(saturday)).isEqualTo(LocalDate.of(2026, 9, 28));
        assertThat(WeekStart.MONDAY.weekStartOf(saturday.plusDays(1))).isEqualTo(LocalDate.of(2026, 9, 28));
    }

    @Test
    void codesRoundTripAndMatchTheStoredValues() {
        assertThat(WeekStart.SATURDAY.code()).isEqualTo("SAT");
        assertThat(WeekStart.MONDAY.code()).isEqualTo("MON");
        for (WeekStart value : WeekStart.values()) {
            assertThat(WeekStart.fromCode(value.code())).isEqualTo(value);
        }
        assertThatThrownBy(() -> WeekStart.fromCode("SUN")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schoolDaysIsAnImmutableCopy() {
        List<DayOfWeek> days = WeekStart.SATURDAY.schoolDays();
        assertThatThrownBy(() -> days.add(DayOfWeek.THURSDAY)).isInstanceOf(UnsupportedOperationException.class);
    }
}
