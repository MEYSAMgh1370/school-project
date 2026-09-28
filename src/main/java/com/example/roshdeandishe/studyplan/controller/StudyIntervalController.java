package com.example.roshdeandishe.studyplan.controller;

import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import com.example.roshdeandishe.studyplan.StudyPlanFacade;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.CopyResult;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.IntervalId;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.IntervalInfo;
import com.example.roshdeandishe.studyplan.dto.CopyResultResponse;
import com.example.roshdeandishe.studyplan.dto.CopyWeekRequest;
import com.example.roshdeandishe.studyplan.dto.CreateIntervalRequest;
import com.example.roshdeandishe.studyplan.dto.IntervalResponse;
import com.example.roshdeandishe.studyplan.dto.UpdateIntervalRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * بازه‌های مطالعه‌ی یک دانش‌آموز: ثبت دستی، فهرست هفته، پیشنهاد خودکار
 * (پورت table_suggester موshavere)، خالی‌کردن هفته، کپی هفته بین
 * دانش‌آموزان.
 */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}/students/{studentId}/study-intervals")
@RequiresModule("STUDY_PLANNER")
public class StudyIntervalController {

    private final StudyPlanFacade studyPlanFacade;

    public StudyIntervalController(StudyPlanFacade studyPlanFacade) {
        this.studyPlanFacade = studyPlanFacade;
    }

    @PostMapping
    @RequiresPermission("STUDYPLAN_MANAGE")
    public IntervalResponse createInterval(@PathVariable Long schoolId, @PathVariable Long studentId,
                                           @RequestBody CreateIntervalRequest request) {
        TenantContext.requireMatches(schoolId);

        List<Long> subTopicIds = request.subTopicIds() == null ? List.of() : request.subTopicIds();
        IntervalId intervalId = studyPlanFacade.createInterval(
                schoolId, studentId, request.topicId(), subTopicIds, request.start(), request.end());
        return new IntervalResponse(intervalId.value(), studentId, request.topicId(), subTopicIds,
                request.start(), request.end(), false, 0);
    }

    @GetMapping
    @RequiresPermission("STUDYPLAN_VIEW")
    public List<IntervalResponse> getWeek(@PathVariable Long schoolId, @PathVariable Long studentId,
                                          @RequestParam(required = false) LocalDate date) {
        TenantContext.requireMatches(schoolId);

        return studyPlanFacade.getWeek(schoolId, studentId, date).stream()
                .map(StudyIntervalController::toResponse)
                .toList();
    }

    @PatchMapping("/{intervalId}")
    @RequiresPermission("STUDYPLAN_MANAGE")
    public IntervalResponse updateInterval(@PathVariable Long schoolId, @PathVariable Long studentId,
                                           @PathVariable Long intervalId,
                                           @RequestBody UpdateIntervalRequest request) {
        TenantContext.requireMatches(schoolId);

        return toResponse(studyPlanFacade.updateInterval(schoolId, intervalId, request.done(), request.testCount()));
    }

    @DeleteMapping("/{intervalId}")
    @RequiresPermission("STUDYPLAN_MANAGE")
    public void deleteInterval(@PathVariable Long schoolId, @PathVariable Long studentId,
                               @PathVariable Long intervalId) {
        TenantContext.requireMatches(schoolId);

        studyPlanFacade.deleteInterval(schoolId, intervalId);
    }

    @PostMapping("/suggest")
    @RequiresPermission("STUDYPLAN_MANAGE")
    public List<IntervalResponse> suggestWeek(@PathVariable Long schoolId, @PathVariable Long studentId,
                                              @RequestParam LocalDate weekStart) {
        TenantContext.requireMatches(schoolId);

        return studyPlanFacade.suggestWeek(schoolId, studentId, weekStart).stream()
                .map(StudyIntervalController::toResponse)
                .toList();
    }

    @PostMapping("/clear")
    @RequiresPermission("STUDYPLAN_MANAGE")
    public void clearWeek(@PathVariable Long schoolId, @PathVariable Long studentId,
                          @RequestParam LocalDate weekStart) {
        TenantContext.requireMatches(schoolId);

        studyPlanFacade.clearWeek(schoolId, studentId, weekStart);
    }

    @PostMapping("/copy")
    @RequiresPermission("STUDYPLAN_MANAGE")
    public List<CopyResultResponse> copyWeek(@PathVariable Long schoolId, @PathVariable Long studentId,
                                             @RequestBody CopyWeekRequest request) {
        TenantContext.requireMatches(schoolId);

        List<Long> targets = request.targetStudentIds() == null ? List.of() : request.targetStudentIds();
        return studyPlanFacade.copyWeek(schoolId, studentId, request.weekStart(), targets).stream()
                .map(r -> new CopyResultResponse(r.studentId(), r.ok(), r.message()))
                .toList();
    }

    private static IntervalResponse toResponse(IntervalInfo info) {
        return new IntervalResponse(info.id().value(), info.studentId(), info.topicId(), info.subTopicIds(),
                info.start(), info.end(), info.done(), info.testCount());
    }
}
