package com.example.roshdeandishe.academic.dto;

import com.example.roshdeandishe.academic.DescriptiveGradeLevel;
import com.example.roshdeandishe.academic.GradingScheme;
import com.example.roshdeandishe.academic.GradingTerm;

import java.math.BigDecimal;
import java.time.Instant;

public record GradeResponse(Long id, Long studentId, Long classId, String academicYear, GradingTerm term,
                             GradingScheme scheme, DescriptiveGradeLevel descriptiveLevel, BigDecimal numericScore,
                             String comment, Long recordedByUserId, Instant recordedAt) {
}
