package com.example.roshdeandishe.studyplan.dto.response;

import java.time.LocalDate;
import java.util.List;

public record ImportWeekCoursesResponse(boolean dryRun, int total, int created, int existing, int failed,
                                        List<Item> items) {

    /** status: CREATED / EXISTING / FAILED؛ reason فقط برای FAILED. */
    public record Item(int index, LocalDate weekStart, String courseTopicCode, String status, String reason) {
    }
}
