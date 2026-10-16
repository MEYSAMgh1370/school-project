package com.example.roshdeandishe.attendance.internal;

import com.example.roshdeandishe.attendance.AttendanceFacade.DaySummary;
import com.example.roshdeandishe.attendance.AttendanceFacade.ReportRow;
import com.example.roshdeandishe.attendance.internal.AttendanceMath.ClassTally;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * حساب‌های «رقمِ مدیر» و گزارش بدون Spring و Docker: کلاسِ گرفته‌شده در برابر ثبت‌نشده، تأخیر، غیبتِ موجه،
 * گردکردن به پایین (۹۹٫۹ → ۹۹) و {@code expected = 0}.
 */
class AttendanceMathTest {

    private static final LocalDate DAY = LocalDate.of(2024, 10, 7);

    @Test
    void untakenClassesAreExcludedFromThePercentageAndNeverCountedPresent() {
        DaySummary summary = AttendanceMath.summarize(DAY, true, List.of(
                new ClassTally(true, 10, 2, 1),
                new ClassTally(true, 10, 0, 0),
                new ClassTally(false, 30, 0, 0)));

        assertThat(summary.absent()).isEqualTo(2);
        assertThat(summary.excusedAbsent()).isEqualTo(1);
        assertThat(summary.expected()).isEqualTo(20);
        assertThat(summary.presentPercent()).isEqualTo(90);
        assertThat(summary.classesTaken()).isEqualTo(2);
        assertThat(summary.classesTotal()).isEqualTo(3);
        assertThat(summary.schoolDay()).isTrue();
    }

    @Test
    void excusedAbsencesAreStillAbsentInThePercentage() {
        // ۱ غایبِ موجه از ۴ نفر: ۷۵٪، نه ۱۰۰٪ (موجه بودن غیبت را حضور نمی‌کند)
        DaySummary summary = AttendanceMath.summarize(DAY, true, List.of(new ClassTally(true, 4, 1, 1)));

        assertThat(summary.absent()).isEqualTo(1);
        assertThat(summary.excusedAbsent()).isEqualTo(1);
        assertThat(summary.presentPercent()).isEqualTo(75);
    }

    @Test
    void percentRoundsDownSoNinetyNinePointNineNeverShowsAsAHundred() {
        // ۱ غایب از ۱۰۰۰ نفر = ۹۹٫۹٪ → ۹۹
        assertThat(AttendanceMath.presentPercent(1000, 1)).isEqualTo(99);
        // ۱ از ۳: ۶۶٫۶٪ → ۶۶
        assertThat(AttendanceMath.presentPercent(3, 1)).isEqualTo(66);
        assertThat(AttendanceMath.presentPercent(30, 0)).isEqualTo(100);
        assertThat(AttendanceMath.presentPercent(5, 5)).isEqualTo(0);
    }

    @Test
    void expectedZeroHasNoFigure() {
        assertThat(AttendanceMath.presentPercent(0, 0)).isNull();
        // کلاسِ گرفته‌شده‌ی بدون دانش‌آموز
        DaySummary empty = AttendanceMath.summarize(DAY, true, List.of(new ClassTally(true, 0, 0, 0)));
        assertThat(empty.presentPercent()).isNull();
        assertThat(empty.classesTaken()).isEqualTo(1);
    }

    @Test
    void noRollTakenYetOnASchoolDayHasNoFigureButCountsTheClasses() {
        DaySummary none = AttendanceMath.summarize(DAY, true, List.of(
                new ClassTally(false, 20, 0, 0), new ClassTally(false, 25, 0, 0)));

        assertThat(none.presentPercent()).isNull();
        assertThat(none.expected()).isZero();
        assertThat(none.classesTaken()).isZero();
        assertThat(none.classesTotal()).isEqualTo(2);
    }

    @Test
    void aNonSchoolDayHasNoFigureAtAll() {
        DaySummary closed = AttendanceMath.summarize(DAY, false, List.of(new ClassTally(true, 10, 3, 0)));

        assertThat(closed.schoolDay()).isFalse();
        assertThat(closed.presentPercent()).isNull();
        assertThat(closed.classesTotal()).isZero();
        assertThat(closed.absent()).isZero();
    }

    @Test
    void reportRowTreatsLateAsPresentAndShowsItSeparately() {
        // ۱۰ روزِ ثبت‌شده، ۲ غایب، ۳ دیرآمده → حاضر ۸ (دیرآمده حاضر است)
        ReportRow row = AttendanceMath.reportRow(1L, "علی", 7L, "پنجم الف", 10, 2, 3);

        assertThat(row.absentDays()).isEqualTo(2);
        assertThat(row.lateDays()).isEqualTo(3);
        assertThat(row.presentDays()).isEqualTo(8);
        assertThat(row.className()).isEqualTo("پنجم الف");
    }

    @Test
    void rangePercentFloorsCountsLateAsPresentAndIsNullWithoutABase() {
        assertThat(AttendanceMath.rangePercent(1000, 1)).isEqualTo(99);   // ۹۹٫۹٪ هرگز ۱۰۰ نیست
        assertThat(AttendanceMath.rangePercent(9, 2)).isEqualTo(77);
        assertThat(AttendanceMath.rangePercent(5, 0)).isEqualTo(100);
        assertThat(AttendanceMath.rangePercent(4, 4)).isZero();
        assertThat(AttendanceMath.rangePercent(0, 0)).isNull();
        assertThat(AttendanceMath.rangePercent(-1, 0)).isNull();
        assertThat(AttendanceMath.rangePercent(3, 5)).isZero(); // هرگز منفی نیست
    }

    @Test
    void reportRowCarriesTheRangePercentAndLateDoesNotLowerIt() {
        assertThat(AttendanceMath.reportRow(1L, "علی", 7L, "کلاس", 10, 2, 5).presentPercent()).isEqualTo(80);
        assertThat(AttendanceMath.reportRow(1L, "علی", 7L, "کلاس", 0, 0, 0).presentPercent()).isNull();
    }

    @Test
    void reportPresentDaysNeverGoNegative() {
        assertThat(AttendanceMath.reportRow(1L, "علی", 7L, "کلاس", 1, 2, 0).presentDays()).isZero();
    }

    @Test
    void reportBaseCountsOnlyTheRollsWhoseRosterContainsTheStudent() {
        // دانش‌آموزِ ۴ بعد از roll اول ثبت‌نام شده؛ ۳ پس از roll دوم رفته است
        Map<Long, Integer> base = AttendanceMath.baseDaysByStudent(List.of(
                Set.of(1L, 2L, 3L), Set.of(1L, 2L, 3L, 4L), Set.of(1L, 2L, 4L)));

        assertThat(base).containsEntry(1L, 3).containsEntry(2L, 3).containsEntry(3L, 2).containsEntry(4L, 2);
        assertThat(AttendanceMath.baseDaysByStudent(List.of())).isEmpty();
    }

    @Test
    void nameFilterIsACaseInsensitiveContains() {
        assertThat(AttendanceMath.nameMatches("Ali Rezaei", "rez")).isTrue();
        assertThat(AttendanceMath.nameMatches("علی رضایی", "رضا")).isTrue();
        assertThat(AttendanceMath.nameMatches("علی رضایی", "  رضا  ")).isTrue();
        assertThat(AttendanceMath.nameMatches("علی رضایی", "محمد")).isFalse();
        assertThat(AttendanceMath.nameMatches("علی رضایی", null)).isTrue();
        assertThat(AttendanceMath.nameMatches("علی رضایی", "  ")).isTrue();
        assertThat(AttendanceMath.nameMatches(null, "علی")).isFalse();
        assertThat(AttendanceMath.nameMatches(null, null)).isTrue();
    }
}
