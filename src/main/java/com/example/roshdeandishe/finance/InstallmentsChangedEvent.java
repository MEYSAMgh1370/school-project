package com.example.roshdeandishe.finance;

import java.time.Instant;
import java.util.List;

/**
 * منتشر می‌شود هر بار که طرحِ اقساطِ یک فاکتور واقعاً تغییر می‌کند: ساخت/بازشمارش، ویرایشِ دستی، و تقسیمِ
 * دوباره‌ی خودکار پس از تخفیف یا برداشته شدنِ تخفیفِ مشروط. اگر {@code before} با {@code after} برابر باشد
 * منتشر نمی‌شود. مصرف‌کننده‌ی فعلی listener ثبت audit همین ماژول است؛ {@code actorUserId} nullable است
 * (null = سیستم).
 */
public record InstallmentsChangedEvent(
        Long schoolId,
        Long invoiceId,
        Long studentId,
        InstallmentsChangeCause cause,
        List<InstallmentSnapshot> before,
        List<InstallmentSnapshot> after,
        Long actorUserId,
        Instant occurredAt
) {
}
