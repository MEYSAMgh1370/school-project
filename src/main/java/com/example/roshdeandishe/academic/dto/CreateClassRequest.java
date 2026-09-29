package com.example.roshdeandishe.academic.dto;

import com.example.roshdeandishe.academic.GradingScheme;

/**
 * {@code gradingScheme} اختیاری است. وقتی صریحاً داده نشود، پیش‌فرضش از
 * روی {@code gradeLevel} مشتق می‌شود - پایه‌ی ۱ تا ۶ → DESCRIPTIVE، ۷ تا
 * ۱۲ → NUMERIC (تصمیم صریح کاربر، آشتی‌دادنِ classification که در PR
 * جداگانه‌ای اضافه شد با شِمای صریحِ per-class که در همین PR ساخته شد -
 * CLAUDE.md §11 Q8). هنوز یک فیلد صریح و قابل override روی خودِ کلاس
 * است، نه چیزی که هر بار خودکار محاسبه شود.
 */
public record CreateClassRequest(String name, Long teacherId, String section, int gradeLevel, GradingScheme gradingScheme) {
    public CreateClassRequest {
        if (gradingScheme == null) {
            gradingScheme = gradeLevel <= 6 ? GradingScheme.DESCRIPTIVE : GradingScheme.NUMERIC;
        }
    }
}
