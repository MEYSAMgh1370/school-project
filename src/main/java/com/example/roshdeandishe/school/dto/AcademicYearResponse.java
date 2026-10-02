package com.example.roshdeandishe.school.dto;

/** {@code open}: اپراتور سال را برای انتخاب باز کرده (فقط سال باز را مدیر می‌تواند سال جاری کند). */
public record AcademicYearResponse(Long id, String code, boolean open) {
}
