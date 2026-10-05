package com.example.roshdeandishe.attendance.dto.response;

import com.example.roshdeandishe.attendance.AttendanceFacade.ExcuseRow;

import java.time.Instant;
import java.time.LocalDate;

/** {@code day} ISO (yyyy-MM-dd)؛ {@code status}: PENDING، APPROVED یا REJECTED. */
public record ExcuseResponse(Long id, Long studentId, String studentName, Long classId, String className,
                             LocalDate day, String note, String status, Instant submittedAt, Instant decidedAt) {

    public static ExcuseResponse from(ExcuseRow row) {
        return new ExcuseResponse(row.id(), row.studentId(), row.studentName(), row.classId(), row.className(),
                row.day(), row.note(), row.status().name(), row.submittedAt(), row.decidedAt());
    }
}
