package com.example.roshdeandishe.school.dto.response;

import java.time.LocalDate;

/**
 * {@code source}: SCHOOL (رویدادِ خودِ مدرسه، قابل حذف) یا PLATFORM (تعطیلیِ سراسری).
 * {@code remindDaysBefore}: null برای PLATFORM و برای رویدادِ بدون یادآوری.
 */
public record YearlyPlanItemResponse(Long id, LocalDate day, String title, String source, Integer remindDaysBefore) {
}
