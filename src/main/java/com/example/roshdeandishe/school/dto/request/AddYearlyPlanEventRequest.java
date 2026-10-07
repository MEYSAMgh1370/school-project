package com.example.roshdeandishe.school.dto.request;

import java.time.LocalDate;

/**
 * {@code day} تاریخ میلادیِ ISO و باید در بازه‌ی سالِ {@code academicYearId} باشد.
 * {@code remindDaysBefore} اختیاری است: چند روز پیش از رویداد یادآوری شروع شود (۰..۳۰؛ null = بدون یادآوری).
 */
public record AddYearlyPlanEventRequest(Long academicYearId, LocalDate day, String title, Integer remindDaysBefore) {
}
