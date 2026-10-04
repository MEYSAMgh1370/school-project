package com.example.roshdeandishe.exam.dto.response;

import com.example.roshdeandishe.exam.ExamType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ExamResponse(Long id, Long schoolId, ExamType type, String title, Long teacherId, Long studentId,
                           LocalDateTime start, LocalDateTime end, int durationMinutes, LocalDate weekStart,
                           List<SectionResponse> sections) {
}
