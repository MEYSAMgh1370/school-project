package com.example.roshdeandishe.exam.dto.request;

import java.time.LocalDateTime;

public record CreateExamRequest(String title, LocalDateTime start, LocalDateTime end, int durationMinutes) {
}
