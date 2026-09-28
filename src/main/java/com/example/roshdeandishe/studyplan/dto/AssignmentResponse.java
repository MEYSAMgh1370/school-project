package com.example.roshdeandishe.studyplan.dto;

import java.time.Instant;

public record AssignmentResponse(Long studentId, Long planId, Instant assignedAt) {
}
