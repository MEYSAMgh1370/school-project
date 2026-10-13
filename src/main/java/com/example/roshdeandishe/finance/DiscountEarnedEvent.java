package com.example.roshdeandishe.finance;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * منتشر می‌شود وقتی تخفیفِ مشروط با تسویه‌ی کاملِ فاکتور تا پیش از مهلت قطعی (EARNED) می‌شود؛
 * مصرف‌کننده‌ی فعلی listener ثبت audit همین ماژول است (بدون actor).
 */
public record DiscountEarnedEvent(
        Long schoolId,
        Long invoiceId,
        Long studentId,
        Long discountId,
        BigDecimal amount,
        Instant occurredAt
) {
}
