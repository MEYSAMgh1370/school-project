package com.example.roshdeandishe.exam;

import com.example.roshdeandishe.exam.ExamFacade.AttemptedStudentInfo;
import com.example.roshdeandishe.exam.ExamFacade.ExamResultSummary;
import com.example.roshdeandishe.exam.ExamFacade.SectionResult;
import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * نتایج آزمون سمت مدرس/ادمین - پورت
 * {@code InstructorsExamResultViewSet} موshavere. فقط studentId
 * برمی‌گرداند، نه نام - نام از طریق iam.MembershipController سمت کلاینت
 * resolve می‌شود، همان الگوی finance.js/students.js (رجوع به کلاس‌داک
 * ExamFacade.listAttemptedStudents).
 * <p>
 * EXAM_VIEW کافی است، نه یک Permission جداگانه برای نتایج - همان سطح
 * حساسیتی که خواندن سایر داده‌های آزمون دارد.
 */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}/exams/{examId}/results")
@RequiresModule("EXAM")
public class ExamResultController {

    private final ExamFacade examFacade;

    public ExamResultController(ExamFacade examFacade) {
        this.examFacade = examFacade;
    }

    @GetMapping
    @RequiresPermission("EXAM_VIEW")
    public List<AttemptedStudentResponse> listAttemptedStudents(@PathVariable Long schoolId,
                                                                 @PathVariable Long examId) {
        TenantContext.requireMatches(schoolId);

        return examFacade.listAttemptedStudents(schoolId, examId).stream()
                .map(ExamResultController::toResponse)
                .toList();
    }

    @GetMapping("/{studentId}")
    @RequiresPermission("EXAM_VIEW")
    public ExamResultResponse getStudentResult(@PathVariable Long schoolId, @PathVariable Long examId,
                                               @PathVariable Long studentId) {
        TenantContext.requireMatches(schoolId);

        return toResponse(examFacade.getStudentResult(schoolId, examId, studentId));
    }

    private static AttemptedStudentResponse toResponse(AttemptedStudentInfo info) {
        return new AttemptedStudentResponse(info.studentId(), info.startedAt());
    }

    private static ExamResultResponse toResponse(ExamResultSummary summary) {
        return new ExamResultResponse(summary.examId().value(), summary.studentId(),
                summary.sections().stream().map(ExamResultController::toResponse).toList(), summary.overallScore());
    }

    private static SectionResultResponse toResponse(SectionResult result) {
        return new SectionResultResponse(result.sectionId().value(), result.name(), result.topicId(),
                result.questionCount(), result.correctCount(), result.incorrectCount(), result.unansweredCount(),
                result.score());
    }

    public record AttemptedStudentResponse(Long studentId, Instant startedAt) {
    }

    public record SectionResultResponse(Long sectionId, String name, Long topicId, int questionCount,
                                        int correctCount, int incorrectCount, int unansweredCount, double score) {
    }

    public record ExamResultResponse(Long examId, Long studentId, List<SectionResultResponse> sections,
                                     double overallScore) {
    }
}
