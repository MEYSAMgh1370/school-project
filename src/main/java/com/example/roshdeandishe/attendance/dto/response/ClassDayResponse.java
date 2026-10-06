package com.example.roshdeandishe.attendance.dto.response;

import com.example.roshdeandishe.attendance.AttendanceFacade.ClassDay;

import java.time.LocalDate;
import java.util.List;

/** {@code status == null} یعنی حاضر؛ {@code day} ISO (yyyy-MM-dd). */
public record ClassDayResponse(Long classId, String className, LocalDate day, boolean taken, boolean recordable,
                               List<StudentMarkResponse> students) {

    public record StudentMarkResponse(Long studentId, String studentName, String status, boolean excused,
                                      String note) {
    }

    public static ClassDayResponse from(ClassDay day) {
        return new ClassDayResponse(day.classId(), day.className(), day.day(), day.taken(), day.recordable(),
                day.students().stream()
                        .map(s -> new StudentMarkResponse(s.studentId(), s.studentName(),
                                s.status() == null ? null : s.status().name(), s.excused(), s.note()))
                        .toList());
    }
}
