package com.example.roshdeandishe.attendance.dto.response;

import com.example.roshdeandishe.attendance.AttendanceFacade.TeacherReportRow;

import java.util.List;

public record TeacherReportRowResponse(Long teacherUserId, String teacherName, int classCount, int daysTaken,
                                       int absentDays, int lateDays, int presentDays, Integer presentPercent,
                                       List<ClassReportRowResponse> classes) {

    public static TeacherReportRowResponse from(TeacherReportRow r) {
        return new TeacherReportRowResponse(r.teacherUserId(), r.teacherName(), r.classCount(), r.daysTaken(),
                r.absentDays(), r.lateDays(), r.presentDays(), r.presentPercent(),
                r.classes().stream().map(ClassReportRowResponse::from).toList());
    }
}
