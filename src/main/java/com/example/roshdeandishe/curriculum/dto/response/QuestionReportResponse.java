package com.example.roshdeandishe.curriculum.dto.response;

import com.example.roshdeandishe.curriculum.CurriculumFacade.QuestionReportInfo;
import com.example.roshdeandishe.curriculum.CurriculumFacade.ReportStatus;
import com.example.roshdeandishe.curriculum.CurriculumFacade.ReportType;

import java.time.Instant;

public record QuestionReportResponse(
        Long id,
        Long questionId,
        String questionText,
        boolean platformQuestion,
        Long reporterUserId,
        Long schoolId,
        ReportType reportType,
        String comment,
        ReportStatus status,
        String resolutionNote,
        Instant createdAt,
        Instant updatedAt
) {

    public static QuestionReportResponse from(QuestionReportInfo info) {
        return new QuestionReportResponse(
                info.id().value(), info.questionId(), info.questionText(), info.platformQuestion(),
                info.reporterUserId(), info.schoolId(), info.reportType(), info.comment(), info.status(),
                info.resolutionNote(), info.createdAt(), info.updatedAt()
        );
    }
}
