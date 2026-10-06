package com.example.roshdeandishe.school.dto.response;

import java.util.List;

/**
 * {@code year} تهی و {@code items} خالی = مدرسه هنوز سالِ جاری ندارد. {@code year} تاریخ‌های پیش‌فرض را
 * دارد و {@code range} بازه‌ی مؤثرِ مدرسه را (تهی وقتی {@code year} تهی است).
 */
public record YearlyPlanResponse(AcademicYearResponse year, YearlyPlanRangeResponse range,
                                 List<YearlyPlanItemResponse> items) {
}
