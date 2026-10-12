package com.example.roshdeandishe.curriculum.controller;

import com.example.roshdeandishe.curriculum.CurriculumFacade;
import com.example.roshdeandishe.curriculum.CurriculumFacade.ReportStatus;
import com.example.roshdeandishe.curriculum.dto.request.UpdateQuestionReportStatusRequest;
import com.example.roshdeandishe.curriculum.dto.response.QuestionReportResponse;
import com.example.roshdeandishe.curriculum.shared.CurriculumError;
import com.example.roshdeandishe.curriculum.shared.CurriculumException;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * صف گزارش‌های سؤال‌های پلتفرمی (D9): فقط مدیر پلتفرم، با مجوز اختصاصیِ PLATFORM_QUESTION_REPORT_MANAGE (مسیر پلتفرم: بدون
 * TenantContext و بدون {@code @RequiresModule}، مجوز در PlatformContext سنجیده می‌شود).
 */
@RestController
public class PlatformQuestionReportController {

    private final CurriculumFacade curriculumFacade;

    public PlatformQuestionReportController(CurriculumFacade curriculumFacade) {
        this.curriculumFacade = curriculumFacade;
    }

    @GetMapping("/api/platform/question-reports")
    @RequiresPermission("PLATFORM_QUESTION_REPORT_MANAGE")
    public List<QuestionReportResponse> list(@RequestParam(required = false) ReportStatus status) {
        return curriculumFacade.listPlatformReports(status).stream()
                .map(QuestionReportResponse::from)
                .toList();
    }

    @PatchMapping("/api/platform/question-reports/{reportId}/status")
    @RequiresPermission("PLATFORM_QUESTION_REPORT_MANAGE")
    public QuestionReportResponse updateStatus(@PathVariable Long reportId,
                                               @RequestBody UpdateQuestionReportStatusRequest request) {
        if (request == null) {
            throw new CurriculumException(new CurriculumError.InvalidReport("وضعیت جدید الزامی است."));
        }
        return QuestionReportResponse.from(curriculumFacade.updatePlatformReport(
                reportId, request.status(), request.resolutionNote()));
    }
}
