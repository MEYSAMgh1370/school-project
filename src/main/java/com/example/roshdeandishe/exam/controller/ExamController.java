package com.example.roshdeandishe.exam.controller;

import com.example.roshdeandishe.exam.ExamFacade;
import com.example.roshdeandishe.exam.ExamFacade.ExamId;
import com.example.roshdeandishe.exam.ExamFacade.ExamInfo;
import com.example.roshdeandishe.exam.ExamFacade.SectionId;
import com.example.roshdeandishe.exam.ExamFacade.SectionInfo;
import com.example.roshdeandishe.exam.dto.request.AssignStudentsRequest;
import com.example.roshdeandishe.exam.dto.request.CreateExamRequest;
import com.example.roshdeandishe.exam.dto.request.CreateSectionAutoRequest;
import com.example.roshdeandishe.exam.dto.request.CreateSectionRequest;
import com.example.roshdeandishe.exam.dto.response.ExamResponse;
import com.example.roshdeandishe.exam.dto.response.SectionResponse;
import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * مدیریت آزمون‌های COLLECTIVE (تألیف مدرس/ادمین) + تولید خودکار آزمون
 * WEEKLY (شخصیِ یک دانش‌آموز، از سرفصل‌های مطالعه‌شده‌ی اخیرش) - پورت
 * {@code CollectiveExamViewSet}/{@code SectionViewSet}/
 * {@code WeeklyExamViewSet.auto_create/auto_update} موshavere.
 * <p>
 * تقسیم Permission طبق تصمیم صریح new-module این ماژول: EXAM_MANAGE
 * (تألیف/تخصیص) به SCHOOL_ADMIN و TEACHER هر دو داده می‌شود - همان
 * استدلال CURRICULUM_MANAGE/STUDYPLAN_MANAGE (تألیف کار روزمره‌ی معلم
 * است)؛ EXAM_VIEW (خواندن) هم فقط همین دو نقش - عمداً نه STUDENT/PARENT:
 * دسترسی دانش‌آموز از مسیر جدا («my» + توکن جلسه‌ی آزمون، رجوع به
 * MyExamsController/ExamTakeController) میانجی‌گری می‌شود.
 */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}/exams")
@RequiresModule("EXAM")
public class ExamController {

    private final ExamFacade examFacade;

    public ExamController(ExamFacade examFacade) {
        this.examFacade = examFacade;
    }

    @PostMapping
    @RequiresPermission("EXAM_MANAGE")
    public ExamResponse createExam(@PathVariable Long schoolId, @RequestBody CreateExamRequest request) {
        TenantContext.requireMatches(schoolId);

        ExamId examId = examFacade.createCollectiveExam(schoolId, request.title(), request.start(), request.end(),
                request.durationMinutes());
        return toResponse(examFacade.getExam(schoolId, examId.value()));
    }

    @GetMapping
    @RequiresPermission("EXAM_VIEW")
    public List<ExamResponse> listExams(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);

        return examFacade.listCollectiveExams(schoolId).stream().map(ExamController::toResponse).toList();
    }

    @GetMapping("/{examId}")
    @RequiresPermission("EXAM_VIEW")
    public ExamResponse getExam(@PathVariable Long schoolId, @PathVariable Long examId) {
        TenantContext.requireMatches(schoolId);

        return toResponse(examFacade.getExam(schoolId, examId));
    }

    @PostMapping("/{examId}/assign")
    @RequiresPermission("EXAM_MANAGE")
    public void assignStudents(@PathVariable Long schoolId, @PathVariable Long examId,
                                @RequestBody AssignStudentsRequest request) {
        TenantContext.requireMatches(schoolId);

        examFacade.assignStudents(schoolId, examId, request.studentIds());
    }

    @GetMapping("/{examId}/students")
    @RequiresPermission("EXAM_VIEW")
    public List<Long> listAssignedStudents(@PathVariable Long schoolId, @PathVariable Long examId) {
        TenantContext.requireMatches(schoolId);

        return examFacade.listAssignedStudents(schoolId, examId);
    }

    @PostMapping("/{examId}/sections")
    @RequiresPermission("EXAM_MANAGE")
    public SectionResponse createSection(@PathVariable Long schoolId, @PathVariable Long examId,
                                         @RequestBody CreateSectionRequest request) {
        TenantContext.requireMatches(schoolId);

        SectionId sectionId = examFacade.createSection(schoolId, examId, request.topicId(), request.name(),
                request.questionIds());
        return findSection(schoolId, examId, sectionId);
    }

    /**
     * ساخت خودکار بخش با انتخاب تصادفیِ سؤال از زیردرخت topicId - پورت
     * {@code SectionViewSet.auto_generate} موshavere. {@code lazy=false}
     * (پیش‌فرض) یعنی سؤال ناکافی خطا می‌دهد؛ {@code lazy=true} یعنی همان
     * تعداد موجود (حتی صفر) بدون خطا پذیرفته می‌شود.
     */
    @PostMapping("/{examId}/sections/auto")
    @RequiresPermission("EXAM_MANAGE")
    public SectionResponse createSectionAuto(@PathVariable Long schoolId, @PathVariable Long examId,
                                             @RequestBody CreateSectionAutoRequest request) {
        TenantContext.requireMatches(schoolId);

        SectionId sectionId = examFacade.createSectionAuto(schoolId, examId, request.topicId(), request.name(),
                request.count(), request.lazy() != null && request.lazy());
        return findSection(schoolId, examId, sectionId);
    }

    @GetMapping("/{examId}/sections")
    @RequiresPermission("EXAM_VIEW")
    public List<SectionResponse> listSections(@PathVariable Long schoolId, @PathVariable Long examId) {
        TenantContext.requireMatches(schoolId);

        return examFacade.listSections(schoolId, examId).stream().map(ExamController::toSectionResponse).toList();
    }

    /**
     * تولید/بازتولیدِ خودکار آزمون هفتگیِ یک دانش‌آموز - پورت
     * {@code auto-create}/{@code auto-update} موshavere (یک عملیات
     * جایگزینِ کامل، نه دو تای جدا - رجوع به doc ExamFacade.generateWeeklyExam).
     */
    @PostMapping("/weekly")
    @RequiresPermission("EXAM_MANAGE")
    public ExamResponse generateWeeklyExam(@PathVariable Long schoolId, @RequestParam Long studentId,
                                           @RequestParam LocalDate weekStart) {
        TenantContext.requireMatches(schoolId);

        ExamId examId = examFacade.generateWeeklyExam(schoolId, studentId, weekStart);
        return toResponse(examFacade.getExam(schoolId, examId.value()));
    }

    private SectionResponse findSection(Long schoolId, Long examId, SectionId sectionId) {
        return examFacade.listSections(schoolId, examId).stream()
                .filter(s -> s.id().equals(sectionId))
                .findFirst()
                .map(ExamController::toSectionResponse)
                .orElseThrow();
    }

    private static ExamResponse toResponse(ExamInfo info) {
        return new ExamResponse(info.id().value(), info.schoolId(), info.type(), info.title(), info.teacherId(),
                info.studentId(), info.start(), info.end(), info.durationMinutes(), info.weekStart(),
                info.sections().stream().map(ExamController::toSectionResponse).toList());
    }

    private static SectionResponse toSectionResponse(SectionInfo info) {
        return new SectionResponse(info.id().value(), info.examId().value(), info.topicId(), info.name(),
                info.questionCount());
    }
}
