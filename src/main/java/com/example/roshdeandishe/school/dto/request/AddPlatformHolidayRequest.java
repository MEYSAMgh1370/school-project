package com.example.roshdeandishe.school.dto.request;

import java.time.LocalDate;

/** {@code day} تاریخ میلادیِ ISO و باید در بازه‌ی پیش‌فرضِ سالِ مسیر باشد. */
public record AddPlatformHolidayRequest(LocalDate day, String title) {
}
