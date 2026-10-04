package com.example.roshdeandishe.academic.dto.response;

import com.example.roshdeandishe.academic.DescriptiveGradeLevel;
import com.example.roshdeandishe.academic.GradingScheme;
import com.example.roshdeandishe.academic.GradingTerm;

import java.math.BigDecimal;
import java.time.Instant;

/** {@code courseId}/{@code courseName} خالی یعنی «نمرهٔ کل». */
public record GradeResponse(Long id, Long studentId, Long classId, String academicYear, GradingTerm term,
                             GradingScheme scheme, DescriptiveGradeLevel descriptiveLevel, BigDecimal numericScore,
                             String comment, Long recordedByUserId, Instant recordedAt,
                             Long courseId, String courseName) {
}
