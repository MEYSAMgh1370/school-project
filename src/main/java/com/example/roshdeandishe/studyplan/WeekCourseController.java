package com.example.roshdeandishe.studyplan;

import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.WeekCourseId;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.WeekCourseInfo;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * سرفصل‌های هفتگی یک برنامه - همان دسترسی PlanController (STUDYPLAN_MANAGE
 * برای ثبت، STUDYPLAN_VIEW برای فهرست).
 */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}/study-plans/{planId}/week-courses")
@RequiresModule("STUDY_PLANNER")
public class WeekCourseController {

    private final StudyPlanFacade studyPlanFacade;

    public WeekCourseController(StudyPlanFacade studyPlanFacade) {
        this.studyPlanFacade = studyPlanFacade;
    }

    @PostMapping
    @RequiresPermission("STUDYPLAN_MANAGE")
    public WeekCourseResponse createWeekCourse(@PathVariable Long schoolId, @PathVariable Long planId,
                                               @RequestBody CreateWeekCourseRequest request) {
        TenantContext.requireMatches(schoolId);

        List<Long> subTopicIds = request.subTopicIds() == null ? List.of() : request.subTopicIds();
        WeekCourseId weekCourseId = studyPlanFacade.createWeekCourse(
                schoolId, planId, request.weekStart(), request.courseTopicId(), subTopicIds);
        return new WeekCourseResponse(weekCourseId.value(), planId, request.weekStart(),
                request.courseTopicId(), subTopicIds);
    }

    @GetMapping
    @RequiresPermission("STUDYPLAN_VIEW")
    public List<WeekCourseResponse> listWeekCourses(@PathVariable Long schoolId, @PathVariable Long planId) {
        TenantContext.requireMatches(schoolId);

        return studyPlanFacade.listWeekCourses(schoolId, planId).stream()
                .map(WeekCourseController::toResponse)
                .toList();
    }

    private static WeekCourseResponse toResponse(WeekCourseInfo info) {
        return new WeekCourseResponse(info.id().value(), info.planId().value(), info.weekStart(),
                info.courseTopicId(), info.subTopicIds());
    }

    public record CreateWeekCourseRequest(LocalDate weekStart, Long courseTopicId, List<Long> subTopicIds) {
    }

    public record WeekCourseResponse(Long id, Long planId, LocalDate weekStart, Long courseTopicId,
                                     List<Long> subTopicIds) {
    }
}
