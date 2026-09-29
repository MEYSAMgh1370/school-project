package com.example.roshdeandishe.academic.controller;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.AcademicFacade.GradeId;
import com.example.roshdeandishe.academic.AcademicFacade.GradeInfo;
import com.example.roshdeandishe.academic.GradingTerm;
import com.example.roshdeandishe.academic.dto.GradeResponse;
import com.example.roshdeandishe.academic.dto.RecordGradeRequest;
import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * دفترچه‌نمره‌ی ادمین/معلم: ثبت و مشاهده‌ی نمرات یک کلاس. طبق تصمیم صریح
 * (CLAUDE.md §11 Q8، حل‌شده)، هر دو عملیات این کنترلر عمداً با GRADES_EDIT
 * گیت می‌شوند - نه GRADES_VIEW که STUDENT/PARENT هم دارند (برای «نمره‌های
 * خودم»، رجوع کنید به MyGradesController). اگر مشاهده‌ی این endpoint هم با
 * GRADES_VIEW مجاز بود، یک دانش‌آموز می‌توانست با فراخوانی مستقیمِ همین
 * مسیر، نمره‌ی کل هم‌کلاسی‌هایش را ببیند - همان کلاس باگی که CLASS_VIEW
 * برای ClassController حل کرد، ولی اینجا راه‌حل معکوس است: باریک‌تر کردنِ
 * Permission، نه بازتر کردنِ آن.
 * <p>
 * {@code @RequiresModule("ACADEMIC")} در سطح کلاس - همان لایه‌ی اول کنترل
 * دسترسی دولایه، رجوع کنید به ClassController/StudentController.
 */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}/grades")
@RequiresModule("ACADEMIC")
public class GradeController {

    private final AcademicFacade academicFacade;

    public GradeController(AcademicFacade academicFacade) {
        this.academicFacade = academicFacade;
    }

    @PostMapping
    @RequiresPermission("GRADES_EDIT")
    public GradeResponse recordGrade(@PathVariable Long schoolId, @RequestBody RecordGradeRequest request) {
        TenantContext.requireMatches(schoolId);

        GradeId gradeId = academicFacade.recordGrade(
                schoolId, request.studentId(), request.classId(), request.academicYear(), request.term(),
                request.descriptiveLevel(), request.numericScore(), request.comment());
        return toResponse(academicFacade.getGrade(gradeId.value()));
    }

    @GetMapping
    @RequiresPermission("GRADES_EDIT")
    public List<GradeResponse> listGrades(
            @PathVariable Long schoolId,
            @RequestParam Long classId,
            @RequestParam String academicYear,
            @RequestParam GradingTerm term
    ) {
        TenantContext.requireMatches(schoolId);

        return academicFacade.listGradesForClass(schoolId, classId, academicYear, term).stream()
                .map(GradeController::toResponse)
                .toList();
    }

    private static GradeResponse toResponse(GradeInfo info) {
        return new GradeResponse(
                info.id().value(), info.studentId().value(), info.classId().value(), info.academicYear(),
                info.term(), info.scheme(), info.descriptiveLevel(), info.numericScore(), info.comment(),
                info.recordedByUserId(), info.recordedAt()
        );
    }
}
