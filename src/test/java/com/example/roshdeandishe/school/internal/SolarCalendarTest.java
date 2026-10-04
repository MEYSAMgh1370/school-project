package com.example.roshdeandishe.school.internal;

import com.example.roshdeandishe.school.internal.SolarCalendar.SolarDate;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * تبدیل میلادی←شمسیِ سمتِ سرور (پورتِ همان الگوریتمِ static/js/jalali.js) روی
 * تاریخ‌های مرجعِ تقویمِ رسمی: نوروز هر سال، ۱ مرداد (مرزِ سالِ تحصیلی)، و
 * پایانِ اسفندِ کبیسه (۱۴۰۳ کبیسه است). پورت علاوه بر این روی همه‌ی روزهای
 * ۱۹۹۰ تا ۲۰۶۰ با خودِ jalali.js سنجیده شد (۲۵٬۹۳۳ روز، بدون اختلاف) - آن
 * سنجشِ یک‌باره بود و فایلِ مرجعش نگه داشته نشد.
 */
class SolarCalendarTest {

    private static SolarDate solar(int gy, int gm, int gd) {
        return SolarCalendar.fromGregorian(LocalDate.of(gy, gm, gd));
    }

    @Test
    void nowruzIsTheFirstOfFarvardinEveryYear() {
        assertThat(solar(2021, 3, 21)).isEqualTo(new SolarDate(1400, 1, 1));
        assertThat(solar(2022, 3, 21)).isEqualTo(new SolarDate(1401, 1, 1));
        assertThat(solar(2023, 3, 21)).isEqualTo(new SolarDate(1402, 1, 1));
        assertThat(solar(2024, 3, 20)).isEqualTo(new SolarDate(1403, 1, 1));
        assertThat(solar(2025, 3, 21)).isEqualTo(new SolarDate(1404, 1, 1));
        assertThat(solar(2026, 3, 21)).isEqualTo(new SolarDate(1405, 1, 1));
        assertThat(solar(2027, 3, 21)).isEqualTo(new SolarDate(1406, 1, 1));
    }

    @Test
    void theLeapYearEndsOnTheThirtiethOfEsfandAndANormalYearOnTheTwentyNinth() {
        assertThat(solar(2025, 3, 20)).isEqualTo(new SolarDate(1403, 12, 30));
        assertThat(solar(2026, 3, 20)).isEqualTo(new SolarDate(1404, 12, 29));
    }

    @Test
    void theFirstOfMordadAndTheDayBeforeIt() {
        assertThat(solar(2025, 7, 23)).isEqualTo(new SolarDate(1404, 5, 1));
        assertThat(solar(2026, 7, 22)).isEqualTo(new SolarDate(1405, 4, 31));
        assertThat(solar(2026, 7, 23)).isEqualTo(new SolarDate(1405, 5, 1));
    }

    @Test
    void theSixthAndSeventhMonthsMeetAtMehr() {
        assertThat(solar(2026, 9, 22)).isEqualTo(new SolarDate(1405, 6, 31));
        assertThat(solar(2026, 9, 23)).isEqualTo(new SolarDate(1405, 7, 1));
        assertThat(solar(2026, 10, 2)).isEqualTo(new SolarDate(1405, 7, 10));
    }
}
