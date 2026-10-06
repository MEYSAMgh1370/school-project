package com.example.roshdeandishe.attendance.internal;

import com.example.roshdeandishe.attendance.AttendanceFacade;
import com.example.roshdeandishe.attendance.AttendanceStatus;
import com.example.roshdeandishe.school.SchoolFacade.WeekStart;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * قوانینِ خالصِ «روز» و «بازه» (attendance.md قاعده ۱) بدونِ وابستگی به Spring/پایگاه‌داده، تا مستقیم تست شوند.
 * نتیجه یا {@code null} (روا) یا دلیلِ فارسیِ رد.
 */
final class AttendanceRules {

    static final int MAX_EXCUSE_NOTE = 500;

    private AttendanceRules() {
    }

    /** روزِ درسی = روزِ هفته در الگوی مدرسه (SAT-WED یا MON-FRI) و نه تعطیلِ تقویم. */
    static boolean isSchoolDay(WeekStart weekStart, LocalDate day, boolean closedInCalendar) {
        return weekStart.schoolDays().contains(day.getDayOfWeek()) && !closedInCalendar;
    }

    /** دلیلِ نامعتبر بودنِ {@code day} برای «ثبت» یا {@code null}: باید روزِ درسی باشد و نه آینده. */
    static String recordDayProblem(WeekStart weekStart, LocalDate day, LocalDate today, boolean closedInCalendar) {
        if (day == null) {
            return "روز الزامی است.";
        }
        if (day.isAfter(today)) {
            return "برای روزِ آینده نمی‌توان حضور و غیاب ثبت کرد.";
        }
        if (!weekStart.schoolDays().contains(day.getDayOfWeek())) {
            return "این روز در هفته‌ی درسیِ مدرسه نیست.";
        }
        if (closedInCalendar) {
            return "این روز در تقویم مدرسه تعطیل است.";
        }
        return null;
    }

    /**
     * دانش‌آموزانی که در این ذخیره <b>تازه</b> غایب شده‌اند: ABSENT در ورودی و نه ABSENT در مُهرهای قبلیِ همان
     * (کلاس، روز). ذخیره‌ی دوباره‌ی بی‌تغییر و دیرآمده چیزی نمی‌دهد؛ «غایب ← حاضر ← غایب» دوباره می‌دهد.
     */
    static List<Long> newlyAbsent(Set<Long> previouslyAbsent, List<AttendanceFacade.MarkInput> incoming) {
        return incoming.stream()
                .filter(m -> m.status() == AttendanceStatus.ABSENT)
                .map(AttendanceFacade.MarkInput::studentId)
                .filter(id -> !previouslyAbsent.contains(id))
                .toList();
    }

    /** دلیلِ نامعتبر بودنِ یادداشتِ عذر یا {@code null}: غیرخالی و حداکثر {@value #MAX_EXCUSE_NOTE} نویسه. */
    static String excuseNoteProblem(String note) {
        if (note == null || note.isBlank()) {
            return "یادداشتِ عذر الزامی است.";
        }
        if (note.strip().length() > MAX_EXCUSE_NOTE) {
            return "یادداشتِ عذر حداکثر " + MAX_EXCUSE_NOTE + " نویسه می‌تواند باشد.";
        }
        return null;
    }

    /** دلیلِ نامعتبر بودنِ بازه‌ی گزارش یا {@code null}: دو سر الزامی، «از» نه بعد از «تا»، حداکثر سقف. */
    static String rangeProblem(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return "بازه با «از» و «تا» مشخص می‌شود.";
        }
        if (from.isAfter(to)) {
            return "«از» نباید بعد از «تا» باشد.";
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > AttendanceFacade.MAX_REPORT_DAYS) {
            return "بازه‌ی گزارش حداکثر " + AttendanceFacade.MAX_REPORT_DAYS + " روز است.";
        }
        return null;
    }
}
