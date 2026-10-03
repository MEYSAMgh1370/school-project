package com.example.roshdeandishe.exam.controller;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.exam.ExamFacade;
import com.example.roshdeandishe.exam.ExamFacade.AttemptResult;
import com.example.roshdeandishe.exam.ExamFacade.ExamInfo;
import com.example.roshdeandishe.exam.ExamFacade.ExamResultSummary;
import com.example.roshdeandishe.exam.ExamFacade.SectionInfo;
import com.example.roshdeandishe.exam.ExamFacade.SectionResult;
import com.example.roshdeandishe.exam.dto.AttemptResponse;
import com.example.roshdeandishe.exam.dto.ExamResponse;
import com.example.roshdeandishe.exam.dto.ExamResultResponse;
import com.example.roshdeandishe.exam.dto.SectionResponse;
import com.example.roshdeandishe.exam.dto.SectionResultResponse;
import com.example.roshdeandishe.exam.shared.ExamError;
import com.example.roshdeandishe.exam.shared.ExamException;
import com.example.roshdeandishe.iam.IamFacade;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * «آزمون‌های من در این مدرسه» - همان مدل اعتماد
 * {@code academic.MyEnrollmentController}/{@code finance.MyInvoicesController}:
 * عمداً هیچ {@code @RequiresPermission}/{@code @RequiresModule} ندارد،
 * چون داده فقط درباره‌ی خودِ کاربر احراز هویت‌شده است - studentId از
 * {@code AcademicFacade.findMyEnrollment} resolve می‌شود، هرگز از ورودی
 * کاربر. در ماژول exam (نه iam) چون iam نمی‌تواند به academic وابسته شود -
 * همان دلیل MyEnrollmentController.
 * <p>
 * شروع تلاش (POST .../attempts) توکن جلسه‌ی آزمون را برمی‌گرداند؛ از آن
 * پس تعامل با محتوای آزمون فقط از طریق ExamTakeController و همان توکن
 * است، نه این کنترلر - رجوع به کلاس‌داک ExamFacade.
 */
@RestController
public class MyExamsController {

    private final ExamFacade examFacade;
    private final AcademicFacade academicFacade;
    private final IamFacade iamFacade;

    public MyExamsController(ExamFacade examFacade, AcademicFacade academicFacade, IamFacade iamFacade) {
        this.examFacade = examFacade;
        this.academicFacade = academicFacade;
        this.iamFacade = iamFacade;
    }

    @GetMapping("/api/me/schools/{schoolId}/exams")
    public List<ExamResponse> myExams(@PathVariable Long schoolId) {
        Long userId = iamFacade.currentUserId().value();
        Long studentId = resolveStudentId(userId, schoolId);
        if (studentId == null) {
            return List.of();
        }
        return examFacade.listExamsForStudent(schoolId, studentId).stream()
                .map(MyExamsController::toResponse)
                .toList();
    }

    @PostMapping("/api/me/schools/{schoolId}/exams/{examId}/attempts")
    public AttemptResponse startAttempt(@PathVariable Long schoolId, @PathVariable Long examId) {
        Long userId = iamFacade.currentUserId().value();
        Long studentId = requireStudentId(userId, schoolId);
        AttemptResult result = examFacade.startAttempt(schoolId, examId, studentId);
        return new AttemptResponse(result.token(), result.expireAt());
    }

    @GetMapping("/api/me/schools/{schoolId}/exams/{examId}/result")
    public ExamResultResponse myResult(@PathVariable Long schoolId, @PathVariable Long examId) {
        Long userId = iamFacade.currentUserId().value();
        Long studentId = requireStudentId(userId, schoolId);
        return toResponse(examFacade.getMyResult(schoolId, examId, studentId));
    }

    private Long resolveStudentId(Long userId, Long schoolId) {
        // دانش‌آموزِ این مدرسه یعنی نقشِ فعالِ دانش‌آموز: نه عضوِ سابق و نه کسی که فقط نقشِ دیگری دارد
        // (نه فهرست، نه شروعِ تلاش)
        if (!iamFacade.findActiveRoleCodes(new IamFacade.UserId(userId), schoolId).contains("STUDENT")) {
            return null;
        }
        return academicFacade.findMyEnrollment(userId, schoolId)
                .map(enrollment -> enrollment.studentId().value())
                .orElse(null);
    }

    /**
     * برخلاف myExams (که فقط فهرست خالی برمی‌گرداند)، عملیاتی مثل شروع
     * تلاش/دیدن نتیجه بدون یک StudentEntity واقعی معنا ندارند - وگرنه
     * exceptionِ داخلیِ IamFacade.currentUserId (بدون سشن) با یک 500 خام
     * بیرون می‌زد، نه یک ProblemDetail دامنه‌ای.
     */
    private Long requireStudentId(Long userId, Long schoolId) {
        Long studentId = resolveStudentId(userId, schoolId);
        if (studentId == null) {
            throw new ExamException(new ExamError.CannotAttempt("شما در این مدرسه دانش‌آموز نیستید."));
        }
        return studentId;
    }

    private static ExamResponse toResponse(ExamInfo info) {
        return new ExamResponse(info.id().value(), info.schoolId(), info.type(), info.title(), info.teacherId(),
                info.studentId(), info.start(), info.end(), info.durationMinutes(), info.weekStart(),
                info.sections().stream().map(MyExamsController::toResponse).toList());
    }

    private static SectionResponse toResponse(SectionInfo info) {
        return new SectionResponse(info.id().value(), info.examId().value(), info.topicId(), info.name(),
                info.questionCount());
    }

    private static ExamResultResponse toResponse(ExamResultSummary summary) {
        return new ExamResultResponse(summary.examId().value(), summary.studentId(),
                summary.sections().stream().map(MyExamsController::toResponse).toList(), summary.overallScore());
    }

    private static SectionResultResponse toResponse(SectionResult result) {
        return new SectionResultResponse(result.sectionId().value(), result.name(), result.topicId(),
                result.questionCount(), result.correctCount(), result.incorrectCount(), result.unansweredCount(),
                result.score());
    }
}
