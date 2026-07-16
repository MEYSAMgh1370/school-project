package com.example.roshdeandishe.studyplan;

import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * برنامه‌ی زمانیِ آزادِ دانش‌آموز + بازه‌های مطالعه‌ی هفتگی - پورت
 * IntervalScheduleViewSet + StudyViewSet در study_table/views.py.
 * دسترسیِ ادمین/معلم روی داده‌ی هر دانش‌آموزِ مدرسه (بر خلاف
 * MyStudyWeekController که فقط خودِ کاربر را می‌بیند).
 */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}/students/{studentId}")
@RequiresModule("STUDY_PLANNER")
public class StudyIntervalController {

    private final StudyPlanFacade studyPlanFacade;

    public StudyIntervalController(StudyPlanFacade studyPlanFacade) {
        this.studyPlanFacade = studyPlanFacade;
    }

    @GetMapping("/schedule")
    @RequiresPermission("STUDYPLAN_VIEW")
    public ScheduleResponse getSchedule(@PathVariable Long schoolId, @PathVariable Long studentId) {
        TenantContext.requireMatches(schoolId);

        return toResponse(studyPlanFacade.getOrCreateSchedule(schoolId, studentId));
    }

    @PutMapping("/schedule")
    @RequiresPermission("STUDYPLAN_MANAGE")
    public ScheduleResponse updateSchedule(
            @PathVariable Long schoolId, @PathVariable Long studentId, @RequestBody UpdateScheduleRequest request
    ) {
        TenantContext.requireMatches(schoolId);

        return toResponse(studyPlanFacade.updateCurrentSchedule(schoolId, studentId, request.currentSchedule()));
    }

    @PostMapping("/weeks/{weekStartDate}/suggest")
    @RequiresPermission("STUDYPLAN_MANAGE")
    public List<StudyIntervalResponse> suggestWeek(
            @PathVariable Long schoolId, @PathVariable Long studentId, @PathVariable LocalDate weekStartDate
    ) {
        TenantContext.requireMatches(schoolId);

        return studyPlanFacade.suggestWeek(schoolId, studentId, weekStartDate).stream()
                .map(StudyIntervalController::toResponse)
                .toList();
    }

    @GetMapping("/weeks/{weekStartDate}")
    @RequiresPermission("STUDYPLAN_VIEW")
    public List<StudyIntervalResponse> getWeek(
            @PathVariable Long schoolId, @PathVariable Long studentId, @PathVariable LocalDate weekStartDate
    ) {
        TenantContext.requireMatches(schoolId);

        return studyPlanFacade.getWeek(schoolId, studentId, weekStartDate).stream()
                .map(StudyIntervalController::toResponse)
                .toList();
    }

    @DeleteMapping("/weeks/{weekStartDate}")
    @RequiresPermission("STUDYPLAN_MANAGE")
    public void clearWeek(@PathVariable Long schoolId, @PathVariable Long studentId, @PathVariable LocalDate weekStartDate) {
        TenantContext.requireMatches(schoolId);

        studyPlanFacade.clearWeek(schoolId, studentId, weekStartDate);
    }

    @PostMapping("/weeks/{weekStartDate}/copy")
    @RequiresPermission("STUDYPLAN_MANAGE")
    public List<CopyWeekResult> copyWeek(
            @PathVariable Long schoolId, @PathVariable Long studentId, @PathVariable LocalDate weekStartDate,
            @RequestBody CopyWeekRequest request
    ) {
        TenantContext.requireMatches(schoolId);

        return studyPlanFacade.copyWeek(schoolId, studentId, weekStartDate, request.targetStudentIds());
    }

    @PatchMapping("/intervals/{intervalId}")
    @RequiresPermission("STUDYPLAN_MANAGE")
    public void updateInterval(
            @PathVariable Long schoolId, @PathVariable Long studentId, @PathVariable Long intervalId,
            @RequestBody UpdateIntervalRequest request
    ) {
        TenantContext.requireMatches(schoolId);

        studyPlanFacade.updateStudyInterval(schoolId, intervalId, request.done(), request.testCount());
    }

    static ScheduleResponse toResponse(ScheduleInfo info) {
        return new ScheduleResponse(
                info.schoolId(), info.studentId(), info.defaultSchedule(), info.currentSchedule(),
                info.effectiveSchedule(), info.lastEditedAt()
        );
    }

    static StudyIntervalResponse toResponse(StudyIntervalInfo info) {
        return new StudyIntervalResponse(
                info.id().value(), info.schoolId(), info.studentId(), info.topicId(), info.subTopicIds(),
                info.startTime(), info.endTime(), info.done(), info.testCount()
        );
    }

    public record UpdateScheduleRequest(Map<String, List<DaySlot>> currentSchedule) {
    }

    public record ScheduleResponse(
            Long schoolId, Long studentId, Map<String, List<DaySlot>> defaultSchedule,
            Map<String, List<DaySlot>> currentSchedule, Map<String, List<DaySlot>> effectiveSchedule,
            java.time.LocalDateTime lastEditedAt
    ) {
    }

    public record StudyIntervalResponse(
            Long id, Long schoolId, Long studentId, Long topicId, List<Long> subTopicIds,
            java.time.LocalDateTime startTime, java.time.LocalDateTime endTime, boolean done, int testCount
    ) {
    }

    public record CopyWeekRequest(List<Long> targetStudentIds) {
    }

    public record UpdateIntervalRequest(Boolean done, Integer testCount) {
    }
}
