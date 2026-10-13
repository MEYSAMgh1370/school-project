package com.example.roshdeandishe.finance;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * منتشر می‌شود هر بار که روی یک فاکتور تخفیف ثبت می‌شود
 * ({@code FinanceFacade.applyDiscount}) - همان الگوی PaymentRecordedEvent؛ مصرف‌کننده‌ی
 * فعلی listener ثبت audit همین ماژول است. {@code actorUserId} nullable است.
 */
public record InvoiceDiscountedEvent(
        Long schoolId,
        Long invoiceId,
        Long studentId,
        BigDecimal amount,
        Long actorUserId,
        Instant occurredAt
) {
}
