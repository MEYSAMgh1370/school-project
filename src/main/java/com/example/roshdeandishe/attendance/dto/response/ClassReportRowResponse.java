package com.example.roshdeandishe.attendance.dto.response;

import com.example.roshdeandishe.attendance.AttendanceFacade.ClassReportRow;

public record ClassReportRowResponse(Long classId, String className, int daysTaken, int absentDays, int lateDays,
                                     int presentDays, Integer presentPercent) {

    public static ClassReportRowResponse from(ClassReportRow r) {
        return new ClassReportRowResponse(r.classId(), r.className(), r.daysTaken(), r.absentDays(), r.lateDays(),
                r.presentDays(), r.presentPercent());
    }
}
