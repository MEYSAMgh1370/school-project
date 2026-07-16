package com.example.roshdeandishe.studyplan;

import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * مدیریت برنامه‌های مطالعاتی و تخصیص آن‌ها به دانش‌آموزان. تقسیم Permission
 * طبق تصمیم new-module این ماژول، همان استدلال curriculum: تألیف برنامه
 * کار روزمره‌ی معلم است، پس STUDYPLAN_MANAGE هم به SCHOOL_ADMIN و هم
 * TEACHER داده می‌شود؛ STUDYPLAN_VIEW هم همین دو نقش - نه STUDENT/PARENT
 * (دسترسی دانش‌آموز به برنامه‌ی خودش از طریق endpoint خودسرویسِ جدا
 * است - رجوع به MyStudyWeekController).
 */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}")
@RequiresModule("STUDY_PLANNER")
public class PlanController {

    private final StudyPlanFacade studyPlanFacade;

    public PlanController(StudyPlanFacade studyPlanFacade) {
        this.studyPlanFacade = studyPlanFacade;
    }

    @PostMapping("/plans")
    @RequiresPermission("STUDYPLAN_MANAGE")
    public PlanResponse createPlan(@PathVariable Long schoolId, @RequestBody CreatePlanRequest request) {
        TenantContext.requireMatches(schoolId);

        PlanId planId = studyPlanFacade.createPlan(schoolId, request.teacherId(), request.title(), request.courseInfo());
        return toResponse(studyPlanFacade.getPlan(schoolId, planId.value()));
    }

    @GetMapping("/plans")
    @RequiresPermission("STUDYPLAN_VIEW")
    public List<PlanResponse> listPlans(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);

        return studyPlanFacade.listPlans(schoolId).stream().map(PlanController::toResponse).toList();
    }

    @GetMapping("/plans/{planId}")
    @RequiresPermission("STUDYPLAN_VIEW")
    public PlanResponse getPlan(@PathVariable Long schoolId, @PathVariable Long planId) {
        TenantContext.requireMatches(schoolId);

        return toResponse(studyPlanFacade.getPlan(schoolId, planId));
    }

    @PostMapping("/students/{studentId}/plan-assignment")
    @RequiresPermission("STUDYPLAN_MANAGE")
    public AssignmentResponse assignPlan(
            @PathVariable Long schoolId, @PathVariable Long studentId, @RequestBody AssignPlanRequest request
    ) {
        TenantContext.requireMatches(schoolId);

        studyPlanFacade.assignPlanToStudent(schoolId, studentId, request.planId());
        return new AssignmentResponse(studentId, request.planId());
    }

    @GetMapping("/students/{studentId}/plan-assignment")
    @RequiresPermission("STUDYPLAN_VIEW")
    public Optional<AssignmentResponse> getAssignedPlan(@PathVariable Long schoolId, @PathVariable Long studentId) {
        TenantContext.requireMatches(schoolId);

        return studyPlanFacade.findAssignedPlan(schoolId, studentId)
                .map(planId -> new AssignmentResponse(studentId, planId.value()));
    }

    @PostMapping("/plans/{planId}/week-courses")
    @RequiresPermission("STUDYPLAN_MANAGE")
    public WeekCourseResponse createWeekCourse(
            @PathVariable Long schoolId, @PathVariable Long planId, @RequestBody CreateWeekCourseRequest request
    ) {
        TenantContext.requireMatches(schoolId);

        WeekCourseId id = studyPlanFacade.createWeekCourse(
                schoolId, planId, request.weekStartDate(), request.topicId(), request.subTopicIds()
        );
        return new WeekCourseResponse(
                id.value(), schoolId, planId, request.weekStartDate(), request.topicId(),
                request.subTopicIds() == null ? List.of() : request.subTopicIds()
        );
    }

    @GetMapping("/plans/{planId}/week-courses")
    @RequiresPermission("STUDYPLAN_VIEW")
    public List<WeekCourseResponse> listWeekCourses(@PathVariable Long schoolId, @PathVariable Long planId) {
        TenantContext.requireMatches(schoolId);

        return studyPlanFacade.listWeekCourses(schoolId, planId).stream()
                .map(PlanController::toResponse)
                .toList();
    }

    private static PlanResponse toResponse(PlanInfo info) {
        return new PlanResponse(info.id().value(), info.schoolId(), info.teacherId(), info.title(), info.courseInfo());
    }

    private static WeekCourseResponse toResponse(WeekCourseInfo info) {
        return new WeekCourseResponse(
                info.id().value(), info.schoolId(), info.planId(), info.weekStartDate(), info.topicId(), info.subTopicIds()
        );
    }

    public record CreatePlanRequest(Long teacherId, String title, List<CourseWeight> courseInfo) {
    }

    public record PlanResponse(Long id, Long schoolId, Long teacherId, String title, List<CourseWeight> courseInfo) {
    }

    public record AssignPlanRequest(Long planId) {
    }

    public record AssignmentResponse(Long studentId, Long planId) {
    }

    public record CreateWeekCourseRequest(LocalDate weekStartDate, Long topicId, List<Long> subTopicIds) {
    }

    public record WeekCourseResponse(
            Long id, Long schoolId, Long planId, LocalDate weekStartDate, Long topicId, List<Long> subTopicIds
    ) {
    }
}
