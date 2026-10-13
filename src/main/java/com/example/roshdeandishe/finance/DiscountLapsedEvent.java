package com.example.roshdeandishe.finance;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * منتشر می‌شود وقتی تخفیفِ مشروط بی‌آنکه فاکتور کامل تسویه شود از مهلتش می‌گذرد و برداشته می‌شود (LAPSED)؛
 * مصرف‌کننده‌ی فعلی listener ثبت audit همین ماژول است (بدون actor).
 */
public record DiscountLapsedEvent(
        Long schoolId,
        Long invoiceId,
        Long studentId,
        Long discountId,
        BigDecimal amount,
        Instant occurredAt
) {
}
