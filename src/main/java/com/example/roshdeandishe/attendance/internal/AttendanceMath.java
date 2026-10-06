package com.example.roshdeandishe.attendance.internal;

import com.example.roshdeandishe.attendance.AttendanceFacade.DaySummary;
import com.example.roshdeandishe.attendance.AttendanceFacade.ReportRow;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * حساب‌های خالصِ «رقمِ مدیر» و گزارش (attendance.md) بدونِ Spring/پایگاه‌داده، تا مستقیم تست شوند.
 */
final class AttendanceMath {

    private AttendanceMath() {
    }

    /** شمارِ یک کلاس در یک روز: {@code expected} اعضای کلاس، {@code absent} مُهرهای ABSENT (غایبِ موجه هم)، {@code excusedAbsent} زیرمجموعه‌ی آن. */
    record ClassTally(boolean taken, int expected, int absent, int excusedAbsent) {
    }

    /**
     * فقط کلاس‌هایی که حضور و غیابشان گرفته شده در مجموع می‌آیند؛ کلاسِ ثبت‌نشده فقط در {@code classesTotal} است و
     * هرگز حاضر شمرده نمی‌شود. روزِ غیردرسی: رقمی نیست.
     */
    static DaySummary summarize(LocalDate day, boolean schoolDay, List<ClassTally> classes) {
        if (!schoolDay) {
            return new DaySummary(day, false, 0, 0, 0, null, 0, 0);
        }
        int taken = 0;
        int expected = 0;
        int absent = 0;
        int excused = 0;
        for (ClassTally tally : classes) {
            if (!tally.taken()) {
                continue;
            }
            taken++;
            expected += tally.expected();
            absent += tally.absent();
            excused += tally.excusedAbsent();
        }
        return new DaySummary(day, true, absent, excused, expected, presentPercent(expected, absent),
                taken, classes.size());
    }

    /**
     * {@code floor(100 × (expected − absent) / expected)}؛ تأخیر حاضر است و اینجا نمی‌آید. تقسیمِ صحیحِ اعدادِ
     * نامنفی همان گردکردن به پایین است (۹۹٫۹٪ هرگز ۱۰۰ نمی‌شود). {@code expected == 0}: رقمی نیست ({@code null}).
     */
    static Integer presentPercent(int expected, int absent) {
        if (expected <= 0) {
            return null;
        }
        int present = Math.max(0, expected - absent);
        return (int) (100L * present / expected);
    }

    /**
     * یک ردیفِ گزارش: مبنا = روزهای بازه که حضور و غیابِ کلاس گرفته شده ({@code rollDays})؛
     * {@code present = rollDays − absent} (تأخیر حاضر است و جدا در {@code lateDays} می‌آید).
     */
    static ReportRow reportRow(Long studentId, String studentName, Long classId, String className, int rollDays,
                               int absentDays, int lateDays) {
        return new ReportRow(studentId, studentName, classId, className, absentDays, lateDays,
                Math.max(0, rollDays - absentDays));
    }

    /**
     * مبنای گزارش برای یک کلاس: هر عضوِ فهرستِ هر roll یک روز می‌گیرد ({@code studentId ← شمارِ rollهایی که
     * فهرستشان او را دارد}). دانش‌آموزی که بعد از یک roll ثبت‌نام شده آن roll را نمی‌گیرد.
     */
    static Map<Long, Integer> baseDaysByStudent(Collection<? extends Collection<Long>> rollRosters) {
        Map<Long, Integer> base = new HashMap<>();
        for (Collection<Long> roster : rollRosters) {
            for (Long studentId : new HashSet<>(roster)) {
                base.merge(studentId, 1, Integer::sum);
            }
        }
        return base;
    }

    /**
     * فیلترِ نام: شاملِ حروف (contains) بدونِ حساسیت به بزرگی/کوچکیِ حروف. فیلترِ خالی همه را می‌پذیرد؛ نامِ
     * خالی با فیلترِ غیرخالی نمی‌خواند. نرمال‌سازیِ ی/ک و ارقام عمداً نیست (helperای موجود نیست).
     */
    static boolean nameMatches(String name, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        if (name == null) {
            return false;
        }
        return name.toLowerCase(Locale.ROOT).contains(filter.strip().toLowerCase(Locale.ROOT));
    }
}
