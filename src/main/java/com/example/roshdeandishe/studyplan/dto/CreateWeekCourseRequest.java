package com.example.roshdeandishe.studyplan.dto;

import java.time.LocalDate;
import java.util.List;

public record CreateWeekCourseRequest(LocalDate weekStart, Long courseTopicId, List<Long> subTopicIds) {
}
