package com.example.roshdeandishe.school.dto.request;

import java.time.LocalDate;

/** {@code day} تاریخ میلادیِ ISO و باید در بازه‌ی سالِ {@code academicYearId} باشد. */
public record AddYearlyPlanEventRequest(Long academicYearId, LocalDate day, String title) {
}
