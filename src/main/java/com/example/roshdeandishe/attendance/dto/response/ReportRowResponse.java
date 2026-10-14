package com.example.roshdeandishe.attendance.dto.response;

import com.example.roshdeandishe.attendance.AttendanceFacade.ReportRow;

/**
 * {@code presentDays} = روزهای ثبت‌شده‌ی کلاس − {@code absentDays}؛ تأخیر حاضر است و جدا در {@code lateDays} می‌آید.
 * {@code presentPercent} = درصدِ حضورِ بازه (گردشده به پایین)، تهی اگر مبنا صفر باشد.
 */
public record ReportRowResponse(Long studentId, String studentName, Long classId, String className,
                                int absentDays, int lateDays, int presentDays, Integer presentPercent) {

    public static ReportRowResponse from(ReportRow r) {
        return new ReportRowResponse(r.studentId(), r.studentName(), r.classId(), r.className(), r.absentDays(),
                r.lateDays(), r.presentDays(), r.presentPercent());
    }
}
