package com.example.roshdeandishe.academic.shared;

public final class AcademicException extends RuntimeException {

    private final AcademicError error;

    public AcademicException(AcademicError error) {
        super(describe(error));
        this.error = error;
    }

    public AcademicError error() {
        return error;
    }

    private static String describe(AcademicError error) {
        return switch (error) {
            case AcademicError.StudentNotFound e -> "دانش‌آموز با شناسه " + e.studentId() + " یافت نشد.";
            case AcademicError.ClassNotFound e -> "کلاسی با شناسه " + e.classId() + " یافت نشد.";
            case AcademicError.GuardianNotEligible e ->
                    "کاربر " + e.guardianUserId() + " عضویت ولیِ فعالی در این مدرسه ندارد.";
            case AcademicError.StudentNotInClass e ->
                    "دانش‌آموز " + e.studentId() + " در حال حاضر عضو کلاس " + e.classId() + " نیست.";
            case AcademicError.GradingSchemeMismatch e ->
                    "این کلاس شِمای " + e.expectedScheme() + " دارد.";
            case AcademicError.InvalidGrade e -> e.reason();
            case AcademicError.GradeNotFound e -> "نمره‌ای با شناسه " + e.gradeId() + " یافت نشد.";
            case AcademicError.CourseNotFound e -> "درسی با شناسه " + e.courseId() + " یافت نشد.";
            case AcademicError.CourseNotForClass e ->
                    "درس " + e.courseId() + " برای پایه‌ی کلاس " + e.classId() + " تعریف نشده است.";
            case AcademicError.CourseNotValidForYear e ->
                    "درس " + e.courseId() + " برای سال «" + e.academicYear() + "» معتبر نیست.";
            case AcademicError.CourseNotGradable e -> "درس " + e.courseId() + " در کارنامه نمی‌آید و نمره‌پذیر نیست.";
            case AcademicError.InvalidAcademicYear e -> "سال تحصیلی «" + e.academicYear() + "» در فهرست سال‌ها نیست.";
            case AcademicError.PeriodNotFound e -> "زنگی با شناسه " + e.periodId() + " یافت نشد.";
            case AcademicError.InvalidPeriod e -> e.reason();
            case AcademicError.PeriodOverlaps e -> "زنگ با زنگ " + e.otherPeriodId() + " هم‌پوشانی دارد.";
            case AcademicError.PeriodHasEntries e -> "زنگ " + e.periodId() + " خانه‌ی برنامه دارد و حذف نمی‌شود.";
            case AcademicError.ActivityNotFound e -> "فعالیتی با شناسه " + e.activityId() + " یافت نشد.";
            case AcademicError.ActivityNameTaken e -> "فعالیتی با نام «" + e.name() + "» از قبل هست.";
            case AcademicError.ActivityNotEditable e -> "فعالیت " + e.activityId() + " پیش‌فرضِ پلتفرم است و قابل تغییر نیست.";
            case AcademicError.ActivityInUse e -> "فعالیت " + e.activityId() + " در برنامه به کار رفته و حذف نمی‌شود.";
            case AcademicError.InvalidTimetableEntry e -> e.reason();
            case AcademicError.TeacherNotEligible e ->
                    "کاربر " + e.teacherUserId() + " معلمِ فعالِ این مدرسه نیست.";
            case AcademicError.TeacherDoubleBooked e ->
                    "معلم " + e.teacherUserId() + " در این روز و زنگ در کلاس دیگری هست.";
            case AcademicError.SchoolNotFound e -> "مدرسه‌ای با شناسه " + e.schoolId() + " یافت نشد.";
            case AcademicError.InvalidGradeLevel e -> "پایه‌ی " + e.gradeLevel() + " در بازه‌ی مقطع تحصیلی این مدرسه ("
                    + e.minGrade() + " تا " + e.maxGrade() + ") نیست.";
        };
    }
}
