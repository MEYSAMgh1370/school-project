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
            case AcademicError.SchoolNotFound e -> "مدرسه‌ای با شناسه " + e.schoolId() + " یافت نشد.";
            case AcademicError.InvalidGradeLevel e -> "پایه‌ی " + e.gradeLevel() + " در بازه‌ی مقطع تحصیلی این مدرسه ("
                    + e.minGrade() + " تا " + e.maxGrade() + ") نیست.";
        };
    }
}
