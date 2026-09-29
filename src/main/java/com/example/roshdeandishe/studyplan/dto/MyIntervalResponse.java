package com.example.roshdeandishe.studyplan.dto;

import java.time.LocalDateTime;

public record MyIntervalResponse(Long id, Long topicId, String topicName, LocalDateTime start, LocalDateTime end,
                                 boolean done, int testCount) {
}
