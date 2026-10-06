package com.example.roshdeandishe.school.internal;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** شمسی←میلادی: نقطه‌های مرجع، سالِ کبیسه و وارونِ دقیقِ fromGregorian روی همه‌ی روزها. */
class SolarCalendarToGregorianTest {

    @Test
    void knownReferenceDates() {
        assertThat(SolarCalendar.toGregorian(1405, 1, 1)).isEqualTo(LocalDate.of(2026, 3, 21));
        assertThat(SolarCalendar.toGregorian(1405, 7, 1)).isEqualTo(LocalDate.of(2026, 9, 23));
        assertThat(SolarCalendar.toGregorian(1406, 3, 31)).isEqualTo(LocalDate.of(2027, 6, 21));
        assertThat(SolarCalendar.toGregorian(1403, 1, 1)).isEqualTo(LocalDate.of(2024, 3, 20));
    }

    @Test
    void lastDayOfEsfandInLeapAndNonLeapYears() {
        // ۱۴۰۳ کبیسه است (۳۰ اسفند)، ۱۴۰۴ نه (۲۹ اسفند)
        LocalDate leapLast = SolarCalendar.toGregorian(1403, 12, 30);
        assertThat(leapLast).isEqualTo(LocalDate.of(2025, 3, 20));
        assertThat(SolarCalendar.fromGregorian(leapLast)).isEqualTo(new SolarCalendar.SolarDate(1403, 12, 30));

        LocalDate last = SolarCalendar.toGregorian(1404, 12, 29);
        assertThat(last).isEqualTo(LocalDate.of(2026, 3, 20));
        assertThat(SolarCalendar.fromGregorian(last)).isEqualTo(new SolarCalendar.SolarDate(1404, 12, 29));
    }

    @Test
    void invalidInputIsRejected() {
        assertThatThrownBy(() -> SolarCalendar.toGregorian(1405, 13, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SolarCalendar.toGregorian(1405, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SolarCalendar.toGregorian(1405, 1, 32)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SolarCalendar.toGregorian(1405, 7, 31)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SolarCalendar.toGregorian(1405, 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SolarCalendar.toGregorian(1404, 12, 30)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roundTripsEveryDayOfYears1400To1410() {
        for (int year = 1400; year <= 1410; year++) {
            for (int month = 1; month <= 12; month++) {
                int length = month <= 6 ? 31 : month <= 11 ? 30 : 29;
                if (month == 12) {
                    try {
                        SolarCalendar.toGregorian(year, 12, 30);
                        length = 30;
                    } catch (IllegalArgumentException notLeap) {
                        // اسفندِ سالِ غیرکبیسه ۲۹ روز است
                    }
                }
                for (int day = 1; day <= length; day++) {
                    LocalDate gregorian = SolarCalendar.toGregorian(year, month, day);
                    assertThat(SolarCalendar.fromGregorian(gregorian))
                            .isEqualTo(new SolarCalendar.SolarDate(year, month, day));
                }
            }
        }
    }
}
