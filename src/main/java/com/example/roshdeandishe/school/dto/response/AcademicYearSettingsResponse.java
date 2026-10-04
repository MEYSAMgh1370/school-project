package com.example.roshdeandishe.school.dto.response;

import java.util.List;

/**
 * {@code current} خالی یعنی مدرسه هنوز سال تحصیلی جاری را انتخاب نکرده؛
 * {@code autoPromotion}: ارتقای خودکارِ دانش‌آموزان هنگام تعویضِ سال (فاز ۳).
 */
public record AcademicYearSettingsResponse(AcademicYearResponse current, List<AcademicYearResponse> years,
                                           boolean autoPromotion) {
}
