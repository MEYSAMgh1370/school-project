package com.example.roshdeandishe.school.shared;

public final class SchoolException extends RuntimeException {

    private final SchoolError error;

    public SchoolException(SchoolError error) {
        super(describe(error));
        this.error = error;
    }

    public SchoolError error() {
        return error;
    }

    private static String describe(SchoolError error) {
        return switch (error) {
            case SchoolError.SchoolNotFound e -> "مدرسه‌ای با شناسه " + e.schoolId() + " یافت نشد.";
            case SchoolError.InvalidSlug e -> "نشانی اختصاصی «" + e.slug() + "» نامعتبر است.";
            case SchoolError.SlugAlreadyTaken e -> "نشانی اختصاصی «" + e.slug() + "» قبلاً گرفته شده است.";
            case SchoolError.AcademicYearChanged e -> "سال تحصیلی جاریِ مدرسه در این فاصله عوض شده است.";
            case SchoolError.AcademicYearNotFound e -> "سال تحصیلی‌ای با شناسه " + e.academicYearId() + " یافت نشد.";
            case SchoolError.InvalidSetting e -> e.reason();
        };
    }
}
