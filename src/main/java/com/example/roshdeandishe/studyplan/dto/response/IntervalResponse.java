package com.example.roshdeandishe.studyplan.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record IntervalResponse(Long id, Long studentId, Long topicId, List<Long> subTopicIds,
                               LocalDateTime start, LocalDateTime end, boolean done, int testCount) {
}
