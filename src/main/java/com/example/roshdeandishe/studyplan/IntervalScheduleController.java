package com.example.roshdeandishe.studyplan;

import com.example.roshdeandishe.iam.shared.RequiresModule;
import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.platform.TenantContext;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.ScheduleInfo;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.TimeRange;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.Weekday;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * برنامه‌ی زمانیِ آزادِ هفتگیِ یک دانش‌آموز (default/current) - پیش‌نیاز
 * پیشنهاد خودکار جدول مطالعه (StudyIntervalController.suggest).
 */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}/students/{studentId}/schedule")
@RequiresModule("STUDY_PLANNER")
public class IntervalScheduleController {

    private final StudyPlanFacade studyPlanFacade;

    public IntervalScheduleController(StudyPlanFacade studyPlanFacade) {
        this.studyPlanFacade = studyPlanFacade;
    }

    @GetMapping
    @RequiresPermission("STUDYPLAN_VIEW")
    public ScheduleResponse getSchedule(@PathVariable Long schoolId, @PathVariable Long studentId) {
        TenantContext.requireMatches(schoolId);

        ScheduleInfo info = studyPlanFacade.getSchedule(schoolId, studentId);
        return new ScheduleResponse(info.studentId(), info.defaultSchedule(), info.currentSchedule(),
                info.lastEditedAt(), info.currentActive());
    }

    @PutMapping("/default")
    @RequiresPermission("STUDYPLAN_MANAGE")
    public void setDefaultSchedule(@PathVariable Long schoolId, @PathVariable Long studentId,
                                   @RequestBody Map<Weekday, java.util.List<TimeRange>> schedule) {
        TenantContext.requireMatches(schoolId);

        studyPlanFacade.setDefaultSchedule(schoolId, studentId, schedule);
    }

    @PutMapping("/current")
    @RequiresPermission("STUDYPLAN_MANAGE")
    public void setCurrentSchedule(@PathVariable Long schoolId, @PathVariable Long studentId,
                                   @RequestBody(required = false) Map<Weekday, java.util.List<TimeRange>> schedule) {
        TenantContext.requireMatches(schoolId);

        studyPlanFacade.setCurrentSchedule(schoolId, studentId, schedule);
    }

    public record ScheduleResponse(Long studentId, Map<Weekday, java.util.List<TimeRange>> defaultSchedule,
                                   Map<Weekday, java.util.List<TimeRange>> currentSchedule,
                                   Instant lastEditedAt, boolean currentActive) {
    }
}
