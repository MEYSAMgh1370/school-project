package com.example.roshdeandishe.attendance.shared;

import com.example.roshdeandishe.platform.ProblemDetailFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * نگاشت AttendanceError به ProblemDetail، جدا از handler تا بدون MVC قابل unit-test باشد
 * (الگوی FinanceProblemResolver/IamProblemResolver). پسوندهای type kebab-case‌اند.
 */
@Component
public class AttendanceProblemResolver {

    public ProblemDetail resolve(AttendanceError error) {
        return switch (error) {
            case AttendanceError.InvalidAttendanceDay e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-attendance-day",
                    "روز برای حضور و غیاب معتبر نیست",
                    e.reason()
            );
            case AttendanceError.ClassNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "class-not-found",
                    "کلاس یافت نشد",
                    "کلاسی با شناسه " + e.classId() + " یافت نشد."
            );
            case AttendanceError.SchoolNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "school-not-found",
                    "مدرسه یافت نشد",
                    "مدرسه‌ای با شناسه " + e.schoolId() + " یافت نشد."
            );
            case AttendanceError.ClassNotInCurrentYear e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "class-not-in-current-year",
                    "کلاس برای سال جاری نیست",
                    "این کلاس برای سال «" + e.classYear() + "» است و حضور و غیاب فقط برای کلاس‌های سال جاری («"
                            + e.currentYear() + "») ثبت می‌شود."
            );
            case AttendanceError.StudentNotInClass e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "student-not-in-class",
                    "دانش‌آموز عضو این کلاس نیست",
                    "دانش‌آموز " + e.studentId() + " عضو کلاس " + e.classId() + " نیست."
            );
            case AttendanceError.InvalidMark e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-attendance-mark",
                    "حضور و غیاب نامعتبر است",
                    e.reason()
            );
            case AttendanceError.InvalidRange e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-attendance-range",
                    "بازه‌ی گزارش نامعتبر است",
                    e.reason()
            );
            case AttendanceError.NotYourClass e -> ProblemDetailFactory.problem(
                    HttpStatus.FORBIDDEN, "attendance-not-your-class",
                    "این کلاس از کلاس‌های شما نیست",
                    "فقط برای کلاسی که در آن تدریس می‌کنید می‌توانید حضور و غیاب ثبت کنید."
            );
            case AttendanceError.PastEditNotAllowed e -> ProblemDetailFactory.problem(
                    HttpStatus.FORBIDDEN, "attendance-past-edit-not-allowed",
                    "ویرایش روزهای گذشته مجاز نیست",
                    "حضور و غیابِ " + e.day() + " گذشته است و فقط روزِ جاری را می‌توانید ثبت یا ویرایش کنید."
            );
        };
    }
}
