package com.example.roshdeandishe.school.internal;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** افزودنِ ماهِ شمسی: همان روزِ ماه، و برش به آخرین روزِ ماهِ مقصد. */
class SolarCalendarAddMonthsTest {

    @Test
    void keepsTheSameSolarDayOfMonth() {
        // ۱۴ مهر ۱۴۰۵ + ۱ = ۱۴ آبان ۱۴۰۵
        LocalDate start = SolarCalendar.toGregorian(1405, 7, 14);
        assertThat(SolarCalendar.addMonths(start, 1)).isEqualTo(SolarCalendar.toGregorian(1405, 8, 14));
        assertThat(SolarCalendar.addMonths(start, 0)).isEqualTo(start);
    }

    @Test
    void clampsToTheLastDayOfShorterMonth() {
        // ۳۱ شهریور + ۱ ماه = ۳۰ مهر (مهر ۳۰ روز دارد)
        LocalDate start = SolarCalendar.toGregorian(1405, 6, 31);
        assertThat(SolarCalendar.addMonths(start, 1)).isEqualTo(SolarCalendar.toGregorian(1405, 7, 30));
        // ۳۰ آبان + ۴ ماه = ۲۹ اسفند ۱۴۰۵ (۱۴۰۵ کبیسه نیست)
        LocalDate aban = SolarCalendar.toGregorian(1405, 8, 30);
        assertThat(SolarCalendar.addMonths(aban, 4)).isEqualTo(SolarCalendar.toGregorian(1405, 12, 29));
    }

    @Test
    void rollsOverTheYear() {
        LocalDate start = SolarCalendar.toGregorian(1405, 11, 15);
        assertThat(SolarCalendar.addMonths(start, 3)).isEqualTo(SolarCalendar.toGregorian(1406, 2, 15));
        assertThat(SolarCalendar.addMonths(start, 13)).isEqualTo(SolarCalendar.toGregorian(1406, 12, 15));
    }
}
