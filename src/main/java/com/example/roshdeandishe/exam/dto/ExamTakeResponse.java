package com.example.roshdeandishe.exam.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ExamTakeResponse(Long id, String title, LocalDateTime start, LocalDateTime end,
                               int durationMinutes, List<SectionTakeResponse> sections) {
}
