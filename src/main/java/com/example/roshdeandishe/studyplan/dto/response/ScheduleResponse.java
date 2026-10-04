package com.example.roshdeandishe.studyplan.dto.response;

import com.example.roshdeandishe.studyplan.StudyPlanFacade.TimeRange;
import com.example.roshdeandishe.studyplan.StudyPlanFacade.Weekday;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ScheduleResponse(Long studentId, Map<Weekday, List<TimeRange>> defaultSchedule,
                               Map<Weekday, List<TimeRange>> currentSchedule,
                               Instant lastEditedAt, boolean currentActive) {
}
