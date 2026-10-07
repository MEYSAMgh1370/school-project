package com.example.roshdeandishe.school.dto.request;

import java.util.List;

/** نقش‌هایی که یادآوری می‌گیرند؛ غیرتهی و هر کد یکی از نقش‌های همان مدرسه. */
public record SetReminderRolesRequest(List<String> roleCodes) {
}
