package com.example.roshdeandishe.exam.dto;

import java.util.List;

public record ExamResultResponse(Long examId, Long studentId, List<SectionResultResponse> sections,
                                 double overallScore) {
}
