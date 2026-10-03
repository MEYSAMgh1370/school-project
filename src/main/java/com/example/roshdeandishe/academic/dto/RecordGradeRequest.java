package com.example.roshdeandishe.academic.dto;

import com.example.roshdeandishe.academic.DescriptiveGradeLevel;
import com.example.roshdeandishe.academic.GradingTerm;

import java.math.BigDecimal;

/** {@code courseId} اختیاری است؛ خالی یعنی «نمرهٔ کل» (رفتار پیش از نمره‌ی هر درس). */
public record RecordGradeRequest(Long studentId, Long classId, String academicYear, GradingTerm term,
                                  DescriptiveGradeLevel descriptiveLevel, BigDecimal numericScore, String comment,
                                  Long courseId) {
}
