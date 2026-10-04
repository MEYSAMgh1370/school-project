package com.example.roshdeandishe.exam.dto.response;

public record SectionResultResponse(Long sectionId, String name, Long topicId, int questionCount,
                                    int correctCount, int incorrectCount, int unansweredCount, double score) {
}
