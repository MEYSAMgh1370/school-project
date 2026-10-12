package com.example.roshdeandishe.exam.dto.response;

import java.time.Instant;

/**
 * گزارش مشکل سؤالِ خودِ دانش‌آموز؛ reportType/status رشته‌اند (OPEN/REVIEWED/FIXED/CLOSED)
 * تا نوع‌های curriculum از API ماژول exam بیرون نزنند.
 */
public record MyQuestionReportResponse(
        Long id,
        Long questionId,
        String reportType,
        String comment,
        String status,
        String resolutionNote,
        Instant createdAt
) {
}
