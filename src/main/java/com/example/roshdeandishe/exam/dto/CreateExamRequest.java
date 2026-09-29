package com.example.roshdeandishe.exam.dto;

import java.time.LocalDateTime;

public record CreateExamRequest(String title, LocalDateTime start, LocalDateTime end, int durationMinutes) {
}
