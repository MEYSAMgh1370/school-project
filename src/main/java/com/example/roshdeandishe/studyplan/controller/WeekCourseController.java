package com.example.roshdeandishe.studyplan.controller;

import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import com.example.roshdeandishe.studyplan.StudyPlanFacade;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.WeekCourseId;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.WeekCourseInfo;
import com.example.roshdeandishe.studyplan.dto.request.CreateWeekCourseRequest;
import com.example.roshdeandishe.studyplan.dto.request.ImportWeekCoursesRequest;
import com.example.roshdeandishe.studyplan.dto.response.ImportWeekCoursesResponse;
import com.example.roshdeandishe.studyplan.dto.response.WeekCourseResponse;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/import")
    @RequiresPermission("STUDYPLAN_MANAGE")
    public ImportWeekCoursesResponse importWeekCourses(@PathVariable Long schoolId, @PathVariable Long planId,
                                                       @RequestBody ImportWeekCoursesRequest request) {
        TenantContext.requireMatches(schoolId);

        List<StudyPlanFacade.WeekCourseImportItem> items = request.items() == null ? null
                : request.items().stream()
                .map(i -> new StudyPlanFacade.WeekCourseImportItem(i.weekStart(), i.courseTopicCode(), i.subTopicCodes()))
                .toList();
        // dryRun غایب = پیش‌نمایش؛ نوشتن فقط با dryRun=false صریح
        boolean dryRun = request.dryRun() == null || request.dryRun();
        StudyPlanFacade.WeekCourseImportResult result = studyPlanFacade.importWeekCourses(schoolId, planId, dryRun, items);
        return new ImportWeekCoursesResponse(result.dryRun(), result.total(), result.created(), result.existing(),
                result.failed(),
                result.items().stream()
                        .map(o -> new ImportWeekCoursesResponse.Item(o.index(), o.weekStart(), o.courseTopicCode(),
                                o.status().name(), o.reason()))
                        .toList());
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
}
