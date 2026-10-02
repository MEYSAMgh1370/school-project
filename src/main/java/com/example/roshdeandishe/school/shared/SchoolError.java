package com.example.roshdeandishe.school.shared;

public sealed interface SchoolError {

    record SchoolNotFound(Long schoolId) implements SchoolError {
    }

    /**
     * نشانی اختصاصی (slug) نامعتبر است - قالب مجاز عمداً همان قالب یک
     * DNS label است (حروف کوچک انگلیسی/رقم، خط تیره فقط در میانه،
     * ۳ تا ۶۳ نویسه) تا در فاز بعدیِ «دامنه‌ی اختصاصی مدرسه» همین slug
     * بدون هیچ migration ای ساب‌دامین شود (tizhooshan.example.com).
     */
    record InvalidSlug(String slug) implements SchoolError {
    }

    record SlugAlreadyTaken(String slug) implements SchoolError {
    }

    /** academicYearId ای که در فهرست سراسری سال‌های تحصیلی نیست. */
    record AcademicYearNotFound(Long academicYearId) implements SchoolError {
    }

    /** سال در فهرست هست ولی اپراتور هنوز آن را برای انتخاب باز نکرده. */
    record AcademicYearNotOpen(Long academicYearId) implements SchoolError {
    }
}
