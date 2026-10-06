package com.example.roshdeandishe.school.dto.request;

import java.time.LocalDate;

/**
 * تاریخ‌ها میلادیِ ISO‌اند. بازه‌ی پیش‌فرضِ سال حداقل است؛ فقط گسترش پذیرفته می‌شود
 * و فرستادنِ تاریخ‌های پیش‌فرض بازه‌ی اختصاصی را پاک می‌کند.
 */
public record SetYearlyPlanRangeRequest(Long academicYearId, LocalDate startDate, LocalDate endDate) {
}
