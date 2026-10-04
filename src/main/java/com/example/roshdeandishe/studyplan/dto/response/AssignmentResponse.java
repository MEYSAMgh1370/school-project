package com.example.roshdeandishe.studyplan.dto.response;

import java.time.Instant;

public record AssignmentResponse(Long studentId, Long planId, Instant assignedAt) {
}
