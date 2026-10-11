package com.example.roshdeandishe.academic.dto.response;

/** یک درس در انتخابگرِ درس‌های پایه؛ {@code topicId} تهی یعنی درس به سرفصل وصل نیست. */
public record CourseCatalogResponse(Long id, int gradeLevel, String name, String track, Long topicId) {
}
