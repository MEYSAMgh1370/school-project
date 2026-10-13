package com.example.roshdeandishe.finance;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * منتشر می‌شود وقتی تخفیفِ مشروط بی‌آنکه فاکتور کامل تسویه شود از مهلتش می‌گذرد و برداشته می‌شود (LAPSED)؛
 * مصرف‌کننده‌ی فعلی listener ثبت audit همین ماژول است (بدون actor). {@code deadline} مهلتِ تخفیف و {@code trigger} محرکِ گذار
 * (sweep، payment، discount یا installments) است و فقط برای جزئیاتِ audit می‌آید.
 */
public record DiscountLapsedEvent(
        Long schoolId,
        Long invoiceId,
        Long studentId,
        Long discountId,
        BigDecimal amount,
        Instant occurredAt,
        java.time.LocalDate deadline,
        String trigger
) {
}
