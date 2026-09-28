package com.example.roshdeandishe.studyplan.dto;

import java.time.LocalDate;
import java.util.List;

public record CopyWeekRequest(LocalDate weekStart, List<Long> targetStudentIds) {
}
