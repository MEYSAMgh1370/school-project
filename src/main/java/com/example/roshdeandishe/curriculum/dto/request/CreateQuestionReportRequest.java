package com.example.roshdeandishe.curriculum.dto.request;

import com.example.roshdeandishe.curriculum.CurriculumFacade.ReportType;

public record CreateQuestionReportRequest(ReportType reportType, String comment) {
}
