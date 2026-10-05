package com.example.roshdeandishe.attendance.dto.response;

import com.example.roshdeandishe.attendance.AttendanceFacade.DaySummary;

import java.time.LocalDate;

/** {@code presentPercent == null}: رقمی نیست (روزِ غیردرسی، یا هنوز هیچ کلاسی ثبت نشده). */
public record DaySummaryResponse(LocalDate day, boolean schoolDay, int absent, int excusedAbsent, int expected,
                                 Integer presentPercent, int classesTaken, int classesTotal) {

    public static DaySummaryResponse from(DaySummary s) {
        return new DaySummaryResponse(s.day(), s.schoolDay(), s.absent(), s.excusedAbsent(), s.expected(),
                s.presentPercent(), s.classesTaken(), s.classesTotal());
    }
}
