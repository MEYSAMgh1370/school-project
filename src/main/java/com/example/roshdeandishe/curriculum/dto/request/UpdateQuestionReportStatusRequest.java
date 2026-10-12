package com.example.roshdeandishe.curriculum.dto.request;

import com.example.roshdeandishe.curriculum.CurriculumFacade.ReportStatus;

public record UpdateQuestionReportStatusRequest(ReportStatus status, String resolutionNote) {
}
