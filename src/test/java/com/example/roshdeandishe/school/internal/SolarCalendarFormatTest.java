package com.example.roshdeandishe.school.internal;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** قالبِ متنیِ تاریخِ شمسی برای پیام‌های سرور: روز، نامِ ماه و سال با رقم فارسی. */
class SolarCalendarFormatTest {

    @Test
    void formatsDayMonthNameAndYearWithPersianDigits() {
        assertThat(SolarCalendar.format(LocalDate.of(2026, 10, 6))).isEqualTo("۱۴ مهر ۱۴۰۵");
        assertThat(SolarCalendar.format(LocalDate.of(2024, 10, 7))).isEqualTo("۱۶ مهر ۱۴۰۳");
        // نخستین روزِ سال و آخرین روزِ اسفند
        assertThat(SolarCalendar.format(LocalDate.of(2026, 3, 21))).isEqualTo("۱ فروردین ۱۴۰۵");
        assertThat(SolarCalendar.format(LocalDate.of(2026, 3, 20))).isEqualTo("۲۹ اسفند ۱۴۰۴");
    }
}
