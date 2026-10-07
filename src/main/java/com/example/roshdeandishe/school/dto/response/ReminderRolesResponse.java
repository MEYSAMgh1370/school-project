package com.example.roshdeandishe.school.dto.response;

import java.util.List;

/**
 * {@code roles}: نقش‌هایی که عضوِ این مدرسه می‌تواند داشته باشد (بدون نقش‌های پلتفرم)؛
 * {@code selected}: کدهای انتخاب‌شده یا null وقتی انتخابی ذخیره نشده (= همه‌ی نقش‌ها).
 */
public record ReminderRolesResponse(List<Role> roles, List<String> selected) {

    public record Role(String code, String title) {
    }
}
