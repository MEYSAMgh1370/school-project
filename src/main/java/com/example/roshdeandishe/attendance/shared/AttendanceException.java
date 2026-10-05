package com.example.roshdeandishe.attendance.shared;

public final class AttendanceException extends RuntimeException {

    private final AttendanceError error;

    public AttendanceException(AttendanceError error) {
        super(describe(error));
        this.error = error;
    }

    public AttendanceError error() {
        return error;
    }

    private static String describe(AttendanceError error) {
        return switch (error) {
            case AttendanceError.InvalidAttendanceDay e -> e.reason();
            case AttendanceError.ClassNotFound e -> "کلاسی با شناسه " + e.classId() + " یافت نشد.";
            case AttendanceError.SchoolNotFound e -> "مدرسه‌ای با شناسه " + e.schoolId() + " یافت نشد.";
            case AttendanceError.ClassNotInCurrentYear e ->
                    "کلاس " + e.classId() + " برای سال «" + e.classYear() + "» است، نه سال جاری «" + e.currentYear() + "».";
            case AttendanceError.StudentNotInClass e ->
                    "دانش‌آموز " + e.studentId() + " عضو کلاس " + e.classId() + " نیست.";
            case AttendanceError.InvalidMark e -> e.reason();
            case AttendanceError.InvalidRange e -> e.reason();
            case AttendanceError.NotYourClass e -> "کلاس " + e.classId() + " از کلاس‌های شما نیست.";
            case AttendanceError.PastEditNotAllowed e ->
                    "ثبت یا ویرایشِ حضور و غیابِ روزِ گذشته (" + e.day() + ") مجاز نیست.";
        };
    }
}
