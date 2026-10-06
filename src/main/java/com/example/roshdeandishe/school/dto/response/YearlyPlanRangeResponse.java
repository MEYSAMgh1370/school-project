package com.example.roshdeandishe.school.dto.response;

import java.time.LocalDate;

/** بازه‌ی مؤثرِ برنامه‌ی سالانه؛ {@code custom} = مدرسه آن را از پیش‌فرضِ سال گسترش داده است. */
public record YearlyPlanRangeResponse(LocalDate startDate, LocalDate endDate, boolean custom) {
}
