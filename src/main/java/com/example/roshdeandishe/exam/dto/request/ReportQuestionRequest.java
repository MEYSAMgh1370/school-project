package com.example.roshdeandishe.exam.dto.request;

import com.example.roshdeandishe.curriculum.CurriculumFacade.ReportType;

public record ReportQuestionRequest(ReportType reportType, String comment) {
}
