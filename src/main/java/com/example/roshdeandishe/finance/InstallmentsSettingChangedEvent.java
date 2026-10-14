package com.example.roshdeandishe.finance;

import java.time.Instant;

/**
 * منتشر می‌شود وقتی کلیدِ «اقساط» یک مدرسه واقعاً عوض می‌شود ({@code FinanceFacade.setInstallmentsEnabled});
 * تنظیمِ همان مقدارِ قبلی رویدادی ندارد. مصرف‌کننده‌ی فعلی listener ثبت audit همین ماژول است.
 */
public record InstallmentsSettingChangedEvent(
        Long schoolId,
        boolean previousEnabled,
        boolean enabled,
        Long actorUserId,
        Instant occurredAt
) {
}
