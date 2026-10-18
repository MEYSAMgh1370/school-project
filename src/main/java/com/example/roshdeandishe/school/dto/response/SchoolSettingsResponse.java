package com.example.roshdeandishe.school.dto.response;

import java.util.List;

/**
 * تنظیماتِ مدرسه؛ هر بخش فقط برای دارنده‌ی مجوزش پر است و برای بقیه null:
 * {@code currentYear}/{@code autoPromotion} (ACADEMIC_YEAR_MANAGE)، {@code installmentsEnabled} (FINANCE_MANAGE)،
 * {@code reminderRoles} (YEARLY_PLAN_MANAGE؛ فهرستِ خالی = همه‌ی نقش‌ها)، {@code bot} (SCHOOL_SETTINGS_MANAGE).
 */
public record SchoolSettingsResponse(AcademicYearResponse currentYear, Boolean autoPromotion,
                                     Boolean installmentsEnabled, List<String> reminderRoles, BotSection bot) {

    /** {@code roles} خالی = همه‌ی نقش‌ها. */
    public record BotSection(boolean enabled, List<String> roles) {
    }
}
