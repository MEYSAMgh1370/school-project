package com.example.roshdeandishe.school.dto;

/**
 * آنچه هر عضو مدرسه می‌بیند: فقط سال جاری ({@code current} خالی = هنوز انتخاب
 * نشده). فهرست همه‌ی سال‌ها فقط برای مدیر است (AcademicYearSettingsResponse).
 */
public record MyAcademicYearResponse(AcademicYearResponse current) {
}
