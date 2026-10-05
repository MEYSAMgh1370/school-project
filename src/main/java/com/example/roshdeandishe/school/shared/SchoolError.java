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

    /**
     * سالِ جاریِ مدرسه در فاصله‌ی خواندن تا ذخیره عوض شده (درخواستِ کهنه یا
     * مدیرِ دیگر)؛ {@code expected} همان بود که کلاینت می‌دید (null = انتخاب‌نشده).
     */
    record AcademicYearChanged(Long expected) implements SchoolError {
    }

    /** academicYearId ای که در فهرست سراسری سال‌های تحصیلی نیست. */
    record AcademicYearNotFound(Long academicYearId) implements SchoolError {
    }

    /** مقدارِ تنظیمِ مدرسه ناقص یا نامعتبر است (مثلاً مقدارِ روشن/خاموش خالی). */
    record InvalidSetting(String reason) implements SchoolError {
    }

    /** ورودیِ تقویم مدرسه ناقص یا نامعتبر است (روز، نوع یا عنوان، یا بازه‌ی نادرست). */
    record InvalidCalendarEntry(String reason) implements SchoolError {
    }

    /** ورودیِ تقویمی با این شناسه در این مدرسه نیست (ورودیِ مدرسه‌ی دیگر هم همین است). */
    record CalendarEntryNotFound(Long entryId) implements SchoolError {
    }
}
