package com.example.roshdeandishe.school.dto.request;

import java.util.List;

/** {@code enabled} الزامی است؛ {@code roles} خالی/null = ربات برای همه‌ی نقش‌ها. */
public record SetBotSettingsRequest(Boolean enabled, List<String> roles) {
}
