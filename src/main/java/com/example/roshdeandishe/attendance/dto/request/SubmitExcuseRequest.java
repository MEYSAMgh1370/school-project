package com.example.roshdeandishe.attendance.dto.request;

import java.time.LocalDate;

/** بدنه‌ی {@code POST /api/me/schools/{id}/attendance/excuses}؛ {@code day} ISO (yyyy-MM-dd)، {@code note} ≤ ۵۰۰. */
public record SubmitExcuseRequest(Long studentId, LocalDate day, String note) {
}
