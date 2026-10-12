package com.example.roshdeandishe.studyplan.dto.request;

import java.time.LocalDate;
import java.util.List;

/** {@code dryRun} غایب = true (اول همیشه پیش‌نمایش). */
public record ImportWeekCoursesRequest(Boolean dryRun, List<Item> items) {

    public record Item(LocalDate weekStart, String courseTopicCode, List<String> subTopicCodes) {
    }
}
