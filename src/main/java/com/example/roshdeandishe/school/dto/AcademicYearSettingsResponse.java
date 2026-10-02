package com.example.roshdeandishe.school.dto;

import java.util.List;

/** {@code current} خالی یعنی مدرسه هنوز سال تحصیلی جاری را انتخاب نکرده. */
public record AcademicYearSettingsResponse(AcademicYearResponse current, List<AcademicYearResponse> years) {
}
