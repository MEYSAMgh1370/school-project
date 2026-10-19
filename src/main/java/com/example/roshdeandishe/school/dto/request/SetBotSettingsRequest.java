package com.example.roshdeandishe.school.dto.request;

import java.util.List;

/**
 * {@code enabled} («پرسش و پاسخ») الزامی است؛ {@code notificationsEnabled} («اعلان‌ها») اختیاری است و
 * null = بدون تغییر؛ {@code roles} خالی/null = ربات برای همه‌ی نقش‌ها.
 */
public record SetBotSettingsRequest(Boolean enabled, Boolean notificationsEnabled, List<String> roles) {
}
