package com.example.roshdeandishe.school;

/**
 * سالِ تحصیلیِ جاریِ یک مدرسه از {@code fromYear} به {@code toYear} عوض شد
 * (openspec/changes/academic-year.md، فاز ۳). فقط وقتی منتشر می‌شود که مدرسه از قبل سالِ جاری داشته
 * است؛ اولین انتخاب رویداد ندارد. ماژول academic با آن ارتقای خودکار را اجرا می‌کند (اگر تنظیم روشن
 * باشد و سالِ جدید بعد از سالِ قبل باشد).
 * <p>
 * schoolId در payload است چون listenerهای async TenantContext ندارند
 * ({@link com.example.roshdeandishe.academic.StudentEnrolledEvent} همین الگو را دارد).
 */
public record CurrentAcademicYearChangedEvent(
        Long schoolId,
        String fromYear,
        int fromStartYear,
        String toYear,
        int toStartYear
) {
}
