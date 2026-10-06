package com.example.roshdeandishe.school.internal;

import java.time.LocalDate;

/**
 * تبدیل تاریخ میلادی به شمسی (جلالی) بدون هیچ وابستگی خارجی - JDK تقویم جلالی
 * ندارد و هیچ کتابخانه‌ی جلالی (ICU4J، ...) در این پروژه نیست. این پورتِ
 * مستقیمِ همان الگوریتم کاظمی/جلالی است که سمت کلاینت در
 * {@code static/js/jalali.js} (تابع toJalali) به کار می‌رود؛ دو پیاده‌سازی با هم
 * هم‌ارزند و روی همه‌ی روزهای ۱۹۹۰ تا ۲۰۶۰ با هم سنجیده شده‌اند. فقط جهتِ
 * میلادی←شمسی لازم است: ساختنِ سال تحصیلی از روی «امروز» (AcademicYearProvisioner).
 * <p>
 * تقسیم و باقی‌مانده مثل {@code ~~(a / b)} در JS به سمت صفر گرد می‌شوند، یعنی
 * همان {@code /} و {@code %} صحیحِ Java.
 */
final class SolarCalendar {

    private static final int[] BREAKS = {
            -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
            1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    };

    record SolarDate(int year, int month, int day) {
    }

    private static final String[] MONTH_NAMES = {
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    };

    private SolarCalendar() {
    }

    /** «۱۴ مهر ۱۴۰۵»: روز، نامِ ماه و سال با رقم فارسی (برای متنِ پیام‌ها؛ DESIGN.md §8). */
    static String format(LocalDate date) {
        SolarDate solar = fromGregorian(date);
        return persianDigits(solar.day()) + " " + MONTH_NAMES[solar.month() - 1] + " " + persianDigits(solar.year());
    }

    private static String persianDigits(int number) {
        StringBuilder out = new StringBuilder();
        for (char c : Integer.toString(number).toCharArray()) {
            out.append((char) ('۰' + (c - '0')));
        }
        return out.toString();
    }

    static SolarDate fromGregorian(LocalDate date) {
        int gy = date.getYear();
        long jdn = g2d(gy, date.getMonthValue(), date.getDayOfMonth());
        int jy = gy - 621;
        JalCal cal = jalCal(jy);
        long jdn1f = g2d(gy, 3, cal.march());
        long k = jdn - jdn1f;
        if (k >= 0) {
            if (k <= 185) {
                return new SolarDate(jy, 1 + (int) (k / 31), (int) (k % 31) + 1);
            }
            k -= 186;
        } else {
            jy -= 1;
            k += 179;
            if (cal.leap() == 1) {
                k += 1;
            }
        }
        return new SolarDate(jy, 7 + (int) (k / 30), (int) (k % 30) + 1);
    }

    /**
     * شمسی←میلادی؛ وارونِ دقیقِ {@link #fromGregorian}. ماه‌های ۱ تا ۶ سی‌ویک روز، ۷ تا ۱۱ سی روز
     * و اسفند ۲۹ روز (در سالِ کبیسه ۳۰) دارند؛ ورودیِ نامعتبر IllegalArgumentException می‌دهد.
     */
    static LocalDate toGregorian(int year, int month, int day) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("ماه شمسی نامعتبر است: " + month);
        }
        JalCal cal = jalCal(year);
        int monthLength = month <= 6 ? 31 : month <= 11 ? 30 : (cal.leap() == 0 ? 30 : 29);
        if (day < 1 || day > monthLength) {
            throw new IllegalArgumentException("روز شمسی نامعتبر است: " + year + "/" + month + "/" + day);
        }
        int dayOfYear = (month <= 6 ? (month - 1) * 31 : 186 + (month - 7) * 30) + day - 1;
        return LocalDate.of(year + 621, 3, cal.march()).plusDays(dayOfYear);
    }

    private record JalCal(int leap, int march) {
    }

    private static JalCal jalCal(int jy) {
        int bl = BREAKS.length;
        int gy = jy + 621;
        int leapJ = -14;
        int jp = BREAKS[0];
        int jump = 0;
        for (int i = 1; i < bl; i++) {
            int jm = BREAKS[i];
            jump = jm - jp;
            if (jy < jm) {
                break;
            }
            leapJ += (jump / 33) * 8 + (jump % 33) / 4;
            jp = jm;
        }
        int n = jy - jp;
        leapJ += (n / 33) * 8 + ((n % 33) + 3) / 4;
        if (jump % 33 == 4 && jump - n == 4) {
            leapJ += 1;
        }
        int leapG = gy / 4 - ((gy / 100 + 1) * 3) / 4 - 150;
        int march = 20 + leapJ - leapG;
        if (jump - n < 6) {
            n = n - jump + (jump / 33) * 33;
        }
        int leap = ((n + 1) % 33 - 1) % 4;
        if (leap == -1) {
            leap = 4;
        }
        return new JalCal(leap, march);
    }

    private static long g2d(int gy, int gm, int gd) {
        long d = ((long) (gy + (gm - 8) / 6 + 100100) * 1461) / 4
                + (153L * ((gm + 9) % 12) + 2) / 5
                + gd - 34840408;
        d = d - ((((long) gy + 100100 + (gm - 8) / 6) / 100) * 3) / 4 + 752;
        return d;
    }
}
