package com.example.roshdeandishe.attendance.internal;

import com.example.roshdeandishe.school.SchoolFacade.WeekStart;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * قاعده‌ی «روزِ درسی» و سقفِ بازه‌ی گزارش (attendance.md قاعده ۱) بدون Spring و Docker. تاریخ‌ها ثابت‌اند:
 * ۲۰۲۴-۱۰-۰۳ پنجشنبه، ۰۴ جمعه، ۰۵ شنبه، ۰۷ دوشنبه و «امروزِ» ساختگی ۰۸ سه‌شنبه.
 */
class AttendanceRulesTest {

    private static final LocalDate THURSDAY = LocalDate.of(2024, 10, 3);
    private static final LocalDate FRIDAY = LocalDate.of(2024, 10, 4);
    private static final LocalDate SATURDAY = LocalDate.of(2024, 10, 5);
    private static final LocalDate MONDAY = LocalDate.of(2024, 10, 7);
    private static final LocalDate TODAY = LocalDate.of(2024, 10, 8);

    @Test
    void saturdayWeekSchoolHasSaturdayThroughWednesday() {
        assertThat(AttendanceRules.recordDayProblem(WeekStart.SATURDAY, SATURDAY, TODAY, false)).isNull();
        assertThat(AttendanceRules.recordDayProblem(WeekStart.SATURDAY, MONDAY, TODAY, false)).isNull();
        assertThat(AttendanceRules.recordDayProblem(WeekStart.SATURDAY, THURSDAY, TODAY, false)).isNotNull();
        assertThat(AttendanceRules.recordDayProblem(WeekStart.SATURDAY, FRIDAY, TODAY, false)).isNotNull();
    }

    @Test
    void mondayWeekSchoolHasMondayThroughFridayAndNotTheWeekend() {
        assertThat(AttendanceRules.recordDayProblem(WeekStart.MONDAY, MONDAY, TODAY, false)).isNull();
        assertThat(AttendanceRules.recordDayProblem(WeekStart.MONDAY, FRIDAY, TODAY, false)).isNull();
        assertThat(AttendanceRules.recordDayProblem(WeekStart.MONDAY, SATURDAY, TODAY, false)).isNotNull();
        assertThat(AttendanceRules.recordDayProblem(WeekStart.MONDAY, SATURDAY.plusDays(1), TODAY, false)).isNotNull();
    }

    @Test
    void aClosedCalendarDayIsNotASchoolDay() {
        assertThat(AttendanceRules.recordDayProblem(WeekStart.SATURDAY, SATURDAY, TODAY, true)).isNotNull();
        assertThat(AttendanceRules.isSchoolDay(WeekStart.SATURDAY, SATURDAY, true)).isFalse();
        assertThat(AttendanceRules.isSchoolDay(WeekStart.SATURDAY, SATURDAY, false)).isTrue();
    }

    @Test
    void aFutureDayIsRejectedAndTodayIsAccepted() {
        assertThat(AttendanceRules.recordDayProblem(WeekStart.SATURDAY, TODAY.plusDays(1), TODAY, false)).isNotNull();
        // امروز (سه‌شنبه) روزِ درسیِ مدرسه‌ی شنبه‌محور است
        assertThat(AttendanceRules.recordDayProblem(WeekStart.SATURDAY, TODAY, TODAY, false)).isNull();
    }

    @Test
    void aMissingDayIsRejected() {
        assertThat(AttendanceRules.recordDayProblem(WeekStart.SATURDAY, null, TODAY, false)).isNotNull();
    }

    @Test
    void reportRangeNeedsBothEndsInOrderAndWithinTheCap() {
        assertThat(AttendanceRules.rangeProblem(null, TODAY)).isNotNull();
        assertThat(AttendanceRules.rangeProblem(TODAY, null)).isNotNull();
        assertThat(AttendanceRules.rangeProblem(TODAY, TODAY.minusDays(1))).isNotNull();
        assertThat(AttendanceRules.rangeProblem(TODAY, TODAY)).isNull();
        // دقیقاً ۳۶۶ روز (شاملِ دو سر) مجاز است، ۳۶۷ روز نه
        LocalDate from = LocalDate.of(2024, 1, 1);
        assertThat(AttendanceRules.rangeProblem(from, from.plusDays(365))).isNull();
        assertThat(AttendanceRules.rangeProblem(from, from.plusDays(366))).isNotNull();
    }
}
