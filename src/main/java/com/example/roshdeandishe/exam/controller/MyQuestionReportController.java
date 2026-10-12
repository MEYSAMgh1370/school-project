package com.example.roshdeandishe.exam.controller;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.curriculum.CurriculumFacade;
import com.example.roshdeandishe.curriculum.CurriculumFacade.QuestionReportInfo;
import com.example.roshdeandishe.curriculum.CurriculumFacade.ReportId;
import com.example.roshdeandishe.curriculum.shared.CurriculumError;
import com.example.roshdeandishe.curriculum.shared.CurriculumException;
import com.example.roshdeandishe.exam.ExamFacade;
import com.example.roshdeandishe.exam.dto.request.ReportQuestionRequest;
import com.example.roshdeandishe.exam.dto.response.MyQuestionReportResponse;
import com.example.roshdeandishe.exam.shared.ExamError;
import com.example.roshdeandishe.exam.shared.ExamException;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * گزارش مشکل سؤال از سمت دانش‌آموز (moshavere-content-import.md، P6): دانش‌آموز ماژول CURRICULUM ندارد، پس از
 * ماژول EXAM گزارش می‌دهد و این کنترلر CurriculumFacade را صدا می‌زند. برخلاف {@code /api/me} (که
 * TenantResolverFilter برایش TenantContext نمی‌سازد و @RequiresModule/@RequiresPermission مدرسه‌ای نمی‌شود)،
 * این مسیرها X-School-ID می‌خواهند. خطاهای CurriculumException (نوع نامعتبر، توضیح بلند) را handler مشترکِ
 * curriculum به ProblemDetail تبدیل می‌کند.
 */
@RestController
@RequestMapping("/api/student/schools/{schoolId}")
@RequiresModule("EXAM")
public class MyQuestionReportController {

    private final ExamFacade examFacade;
    private final CurriculumFacade curriculumFacade;
    private final AcademicFacade academicFacade;
    private final IamFacade iamFacade;

    public MyQuestionReportController(ExamFacade examFacade, CurriculumFacade curriculumFacade,
                                      AcademicFacade academicFacade, IamFacade iamFacade) {
        this.examFacade = examFacade;
        this.curriculumFacade = curriculumFacade;
        this.academicFacade = academicFacade;
        this.iamFacade = iamFacade;
    }

    @PostMapping("/questions/{questionId}/reports")
    @RequiresPermission("QUESTION_REPORT")
    public MyQuestionReportResponse report(@PathVariable Long schoolId, @PathVariable Long questionId,
                                           @RequestBody ReportQuestionRequest request) {
        TenantContext.requireMatches(schoolId);
        if (request == null) {
            throw new CurriculumException(new CurriculumError.InvalidReport("نوع گزارش الزامی است."));
        }
        Long userId = iamFacade.currentUserId().value();
        // غیرِ دانش‌آموز هم همان QuestionNotFound را می‌گیرد: وجود/عدم وجود سؤال لو نمی‌رود
        Long studentId = resolveStudentId(userId, schoolId);
        if (studentId == null) {
            throw new ExamException(new ExamError.QuestionNotFound(questionId));
        }
        examFacade.requireQuestionInStudentExams(schoolId, studentId, questionId);

        ReportId id = curriculumFacade.createReport(
                schoolId, userId, questionId, request.reportType(), request.comment());
        return curriculumFacade.listReportsByReporter(schoolId, userId).stream()
                .filter(r -> r.id().equals(id))
                .findFirst()
                .map(MyQuestionReportController::toResponse)
                .orElseThrow();
    }

    @GetMapping("/question-reports")
    @RequiresPermission("QUESTION_REPORT")
    public List<MyQuestionReportResponse> myReports(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);
        return curriculumFacade.listReportsByReporter(schoolId, iamFacade.currentUserId().value()).stream()
                .map(MyQuestionReportController::toResponse)
                .toList();
    }

    // همان قاعده‌ی MyExamsController.resolveStudentId: نقش فعال STUDENT + ثبت‌نام در همین مدرسه
    private Long resolveStudentId(Long userId, Long schoolId) {
        if (!iamFacade.findActiveRoleCodes(new IamFacade.UserId(userId), schoolId).contains("STUDENT")) {
            return null;
        }
        return academicFacade.findMyEnrollment(userId, schoolId)
                .map(enrollment -> enrollment.studentId().value())
                .orElse(null);
    }

    private static MyQuestionReportResponse toResponse(QuestionReportInfo info) {
        return new MyQuestionReportResponse(info.id().value(), info.questionId(), info.reportType().name(),
                info.comment(), info.status().name(), info.resolutionNote(), info.createdAt());
    }
}
