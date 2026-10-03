package com.example.roshdeandishe.school.dto;

/**
 * {@code expectedCurrentAcademicYearId}: سالِ جاریِ مدرسه همان‌طور که کلاینت می‌بیند
 * ({@code null} = هنوز انتخاب نشده). همیشه باید فرستاده شود: ذخیره فقط وقتی انجام
 * می‌شود که سالِ جاری هنوز همین باشد، وگرنه 409 - تا انتخابِ کهنه انتخابِ جدیدتر
 * را برنگرداند.
 */
public record SetAcademicYearRequest(Long academicYearId, Long expectedCurrentAcademicYearId) {
}
