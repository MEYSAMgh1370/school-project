package com.example.roshdeandishe.school.dto.response;

import java.util.List;

/** {@code year} تهی و {@code items} خالی = مدرسه هنوز سالِ جاری ندارد. */
public record YearlyPlanResponse(AcademicYearResponse year, List<YearlyPlanItemResponse> items) {
}
