package com.example.roshdeandishe.studyplan.controller;

import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import com.example.roshdeandishe.studyplan.StudyPlanFacade;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.AssignmentInfo;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.CourseWeight;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.PlanId;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.PlanInfo;
import com.example.roshdeandishe.studyplan.dto.AssignPlanRequest;
import com.example.roshdeandishe.studyplan.dto.AssignmentResponse;
import com.example.roshdeandishe.studyplan.dto.CreatePlanRequest;
import com.example.roshdeandishe.studyplan.dto.PlanResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * برنامه‌های درسی و تخصیص آن‌ها به دانش‌آموز. طبق تصمیم پاس new-module:
 * STUDYPLAN_MANAGE/STUDYPLAN_VIEW به SCHOOL_ADMIN و TEACHER هر دو داده
 * می‌شود - نویسنده‌ی برنامه در moshavere همان Instructor است
 * (MOSHAVERE_PORT_SPEC.md §6/§2)، همان استدلال CurriculumFacade.
 */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}/study-plans")
@RequiresModule("STUDY_PLANNER")
public class PlanController {

    private final StudyPlanFacade studyPlanFacade;

    public PlanController(StudyPlanFacade studyPlanFacade) {
        this.studyPlanFacade = studyPlanFacade;
    }

    @PostMapping
    @RequiresPermission("STUDYPLAN_MANAGE")
    public PlanResponse createPlan(@PathVariable Long schoolId, @RequestBody CreatePlanRequest request) {
        TenantContext.requireMatches(schoolId);

        List<CourseWeight> weights = request.weights() == null ? List.of() : request.weights();
        PlanId planId = studyPlanFacade.createPlan(schoolId, request.title(), weights);
        return new PlanResponse(planId.value(), schoolId, null, request.title(), weights);
    }

    @GetMapping
    @RequiresPermission("STUDYPLAN_VIEW")
    public List<PlanResponse> listPlans(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);

        return studyPlanFacade.listPlans(schoolId).stream()
                .map(PlanController::toResponse)
                .toList();
    }

    @PostMapping("/{planId}/assignments")
    @RequiresPermission("STUDYPLAN_MANAGE")
    public void assignPlanToStudent(@PathVariable Long schoolId, @PathVariable Long planId,
                                    @RequestBody AssignPlanRequest request) {
        TenantContext.requireMatches(schoolId);

        studyPlanFacade.assignPlanToStudent(schoolId, planId, request.studentId());
    }

    @GetMapping("/{planId}/assignments")
    @RequiresPermission("STUDYPLAN_VIEW")
    public List<AssignmentResponse> listAssignments(@PathVariable Long schoolId, @PathVariable Long planId) {
        TenantContext.requireMatches(schoolId);

        return studyPlanFacade.listAssignments(schoolId, planId).stream()
                .map(a -> new AssignmentResponse(a.studentId(), a.planId().value(), a.assignedAt()))
                .toList();
    }

    private static PlanResponse toResponse(PlanInfo info) {
        return new PlanResponse(info.id().value(), info.schoolId(), info.teacherId(), info.title(), info.weights());
    }
}
