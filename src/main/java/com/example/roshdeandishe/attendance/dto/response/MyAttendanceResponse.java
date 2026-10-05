package com.example.roshdeandishe.attendance.dto.response;

import com.example.roshdeandishe.attendance.AttendanceFacade.MyDayAttendance;
import com.example.roshdeandishe.attendance.AttendanceFacade.MyStudentAttendance;

import java.time.LocalDate;
import java.util.List;

/**
 * پاسخِ {@code GET /api/me/schools/{id}/attendance}: فقط استثناها؛ {@code status} ABSENT یا LATE، {@code day} ISO،
 * {@code excuse} تهی = عذری ثبت نشده، {@code own} = دانش‌آموزِ خودِ کاربر (نه فرزند).
 */
public record MyAttendanceResponse(List<StudentResponse> students) {

    public record StudentResponse(Long studentId, String studentName, boolean own, List<DayResponse> days) {
    }

    public record DayResponse(LocalDate day, String status, boolean excused, ExcuseSummary excuse) {
    }

    public record ExcuseSummary(Long id, String status, String note) {
    }

    public static MyAttendanceResponse from(List<MyStudentAttendance> students) {
        return new MyAttendanceResponse(students.stream()
                .map(s -> new StudentResponse(s.studentId(), s.studentName(), s.own(),
                        s.days().stream().map(MyAttendanceResponse::day).toList()))
                .toList());
    }

    private static DayResponse day(MyDayAttendance d) {
        return new DayResponse(d.day(), d.status().name(), d.excused(),
                d.excuse() == null ? null
                        : new ExcuseSummary(d.excuse().id(), d.excuse().status().name(), d.excuse().note()));
    }
}
