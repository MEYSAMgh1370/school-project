package com.example.roshdeandishe.curriculum.controller;

import com.example.roshdeandishe.curriculum.CurriculumFacade;
import com.example.roshdeandishe.curriculum.CurriculumFacade.ReportId;
import com.example.roshdeandishe.curriculum.CurriculumFacade.ReportStatus;
import com.example.roshdeandishe.curriculum.dto.request.CreateQuestionReportRequest;
import com.example.roshdeandishe.curriculum.dto.request.UpdateQuestionReportStatusRequest;
import com.example.roshdeandishe.curriculum.dto.response.QuestionReportResponse;
import com.example.roshdeandishe.curriculum.shared.CurriculumError;
import com.example.roshdeandishe.curriculum.shared.CurriculumException;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * گزارش مشکل سؤال، سمت مدرسه (moshavere-content-import.md، D8-D10، P6). ثبت با QUESTION_REPORT (دانش‌آموز،
 * معلم، مدیر مدرسه)؛ فهرست با CURRICULUM_VIEW؛ تغییر وضعیت با CURRICULUM_MANAGE و فقط برای گزارشِ سؤال‌های
 * اختصاصیِ همین مدرسه - گزارشِ سؤال پلتفرمی را مدیر پلتفرم می‌بندد (PlatformQuestionReportController).
 */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}")
@RequiresModule("CURRICULUM")
public class QuestionReportController {

    private final CurriculumFacade curriculumFacade;
    private final IamFacade iamFacade;

    public QuestionReportController(CurriculumFacade curriculumFacade, IamFacade iamFacade) {
        this.curriculumFacade = curriculumFacade;
        this.iamFacade = iamFacade;
    }

    @PostMapping("/questions/{questionId}/reports")
    @RequiresPermission("QUESTION_REPORT")
    public QuestionReportResponse createReport(@PathVariable Long schoolId, @PathVariable Long questionId,
                                               @RequestBody CreateQuestionReportRequest request) {
        TenantContext.requireMatches(schoolId);
        if (request == null) {
            throw new CurriculumException(new CurriculumError.InvalidReport("نوع گزارش الزامی است."));
        }

        ReportId id = curriculumFacade.createReport(
                schoolId, iamFacade.currentUserId().value(), questionId, request.reportType(), request.comment());
        return curriculumFacade.listReportsByReporter(schoolId, iamFacade.currentUserId().value()).stream()
                .filter(r -> r.id().equals(id))
                .findFirst()
                .map(QuestionReportResponse::from)
                .orElseThrow();
    }

    @GetMapping("/question-reports")
    @RequiresPermission("CURRICULUM_VIEW")
    public List<QuestionReportResponse> listReports(@PathVariable Long schoolId,
                                                    @RequestParam(required = false) ReportStatus status) {
        TenantContext.requireMatches(schoolId);

        return curriculumFacade.listReportsForSchool(schoolId, status).stream()
                .map(QuestionReportResponse::from)
                .toList();
    }

    @PatchMapping("/question-reports/{reportId}/status")
    @RequiresPermission("CURRICULUM_MANAGE")
    public QuestionReportResponse updateStatus(@PathVariable Long schoolId, @PathVariable Long reportId,
                                               @RequestBody UpdateQuestionReportStatusRequest request) {
        TenantContext.requireMatches(schoolId);
        if (request == null) {
            throw new CurriculumException(new CurriculumError.InvalidReport("وضعیت جدید الزامی است."));
        }

        return QuestionReportResponse.from(curriculumFacade.updateSchoolReport(
                schoolId, reportId, request.status(), request.resolutionNote()));
    }
}
