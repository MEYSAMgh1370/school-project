package com.example.roshdeandishe.studyplan.dto.request;

import java.time.LocalDateTime;
import java.util.List;

public record CreateIntervalRequest(Long topicId, List<Long> subTopicIds, LocalDateTime start, LocalDateTime end) {
}
