package com.example.roshdeandishe.attendance.dto.response;

import com.example.roshdeandishe.attendance.AttendanceFacade.ClassAbsentees;

import java.util.List;

/** {@code taken == false} یعنی «ثبت نشده» (نه «هیچ‌کس غایب نبود»). */
public record ClassAbsenteesResponse(Long classId, String className, boolean taken, int expected, int absent,
                                     int late, int excusedAbsent, List<AbsenteeResponse> students) {

    public record AbsenteeResponse(Long studentId, String studentName, String status, boolean excused,
                                   String note) {
    }

    public static ClassAbsenteesResponse from(ClassAbsentees c) {
        return new ClassAbsenteesResponse(c.classId(), c.className(), c.taken(), c.expected(), c.absent(), c.late(),
                c.excusedAbsent(),
                c.students().stream()
                        .map(a -> new AbsenteeResponse(a.studentId(), a.studentName(), a.status().name(),
                                a.excused(), a.note()))
                        .toList());
    }
}
