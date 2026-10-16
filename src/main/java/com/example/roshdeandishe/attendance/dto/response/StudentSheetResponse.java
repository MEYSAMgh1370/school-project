package com.example.roshdeandishe.attendance.dto.response;

import com.example.roshdeandishe.attendance.AttendanceFacade.SheetDay;
import com.example.roshdeandishe.attendance.AttendanceFacade.StudentSheet;

import java.time.LocalDate;
import java.util.List;

/** برگه‌ی چاپیِ یک دانش‌آموز؛ {@code days} فقط غیبت و تأخیر (ISO)، به ترتیبِ روز و کلاس. */
public record StudentSheetResponse(Long studentId, String studentName, LocalDate from, LocalDate to,
                                   Integer presentPercent, List<ReportRowResponse> rows, List<DayResponse> days) {

    public record DayResponse(String day, Long classId, String className, String status, boolean excused,
                              String note) {

        static DayResponse from(SheetDay d) {
            return new DayResponse(d.day().toString(), d.classId(), d.className(), d.status().name(), d.excused(),
                    d.note());
        }
    }

    public static StudentSheetResponse from(StudentSheet s) {
        return new StudentSheetResponse(s.studentId(), s.studentName(), s.from(), s.to(), s.presentPercent(),
                s.rows().stream().map(ReportRowResponse::from).toList(),
                s.days().stream().map(DayResponse::from).toList());
    }
}
