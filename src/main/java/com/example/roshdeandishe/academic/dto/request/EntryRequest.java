package com.example.roshdeandishe.academic.dto.request;

public record EntryRequest(Long periodId, String weekday, Long courseId, Long activityId,
                           Long teacherUserId) {
}
