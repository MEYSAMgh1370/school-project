package com.example.roshdeandishe.academic.dto.response;

import com.example.roshdeandishe.academic.TimetableDay;
import com.example.roshdeandishe.academic.TimetableFacade.EntryInfo;

public record EntryResponse(Long id, Long classId, String className, Long periodId, TimetableDay weekday,
                            Long courseId, String courseName, Long activityId, String activityName,
                            Long teacherUserId, String teacherName) {
    public static EntryResponse from(EntryInfo e, String teacherName) {
        return new EntryResponse(e.id(), e.classId(), e.className(), e.periodId(), e.weekday(),
                e.courseId(), e.courseName(), e.activityId(), e.activityName(),
                e.teacherUserId(), teacherName);
    }
}
