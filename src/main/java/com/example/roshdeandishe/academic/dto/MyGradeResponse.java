package com.example.roshdeandishe.academic.dto;

import com.example.roshdeandishe.academic.AcademicFacade.GradeInfo;
import com.example.roshdeandishe.academic.DescriptiveGradeLevel;
import com.example.roshdeandishe.academic.GradingScheme;
import com.example.roshdeandishe.academic.GradingTerm;

import java.math.BigDecimal;
import java.time.Instant;

public record MyGradeResponse(Long id, Long studentId, Long classId, String academicYear, GradingTerm term,
                               GradingScheme scheme, DescriptiveGradeLevel descriptiveLevel, BigDecimal numericScore,
                               String comment, Instant recordedAt) {
    public static MyGradeResponse from(GradeInfo info) {
        return new MyGradeResponse(info.id().value(), info.studentId().value(), info.classId().value(),
                info.academicYear(), info.term(), info.scheme(), info.descriptiveLevel(), info.numericScore(),
                info.comment(), info.recordedAt());
    }
}
