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
        };
    }
}
