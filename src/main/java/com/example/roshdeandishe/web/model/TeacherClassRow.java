package com.example.roshdeandishe.web.model;

/**
 * یک کلاسِ معلم روی پیشخوان («کلاس‌های من»). {@code attendance}: {@code TAKEN}/{@code PENDING} یعنی حضور و غیابِ
 * امروزِ این کلاس ثبت شده/نشده؛ {@code UNAVAILABLE} یعنی مدرسه ماژول حضور و غیاب را ندارد و وضعیتی نشان داده نمی‌شود.
 */
public record TeacherClassRow(Long classId, String name, String section, int gradeLevel, int studentCount,
                              AttendanceState attendance) {

    public enum AttendanceState { TAKEN, PENDING, UNAVAILABLE }

    public boolean taken() {
        return attendance == AttendanceState.TAKEN;
    }

    public boolean pending() {
        return attendance == AttendanceState.PENDING;
    }

    public boolean hasSection() {
        return section != null && !section.isBlank();
    }
}
