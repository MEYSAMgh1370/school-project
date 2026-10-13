package com.example.roshdeandishe.finance;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * منتشر می‌شود هر بار که روی یک فاکتور تخفیف ثبت می‌شود
 * ({@code FinanceFacade.applyDiscount}) - همان الگوی PaymentRecordedEvent؛ مصرف‌کننده‌ی
 * فعلی listener ثبت audit همین ماژول است. {@code actorUserId} nullable است. {@code discountId}، {@code reason}،
 * {@code deadline} (خالی = بی‌قیدوشرط) و {@code status} (در لحظه‌ی ثبت: APPLIED یا PENDING) جزئیاتِ audit هستند.
 */
public record InvoiceDiscountedEvent(
        Long schoolId,
        Long invoiceId,
        Long studentId,
        BigDecimal amount,
        Long actorUserId,
        Instant occurredAt,
        Long discountId,
        String reason,
        java.time.LocalDate deadline,
        DiscountStatus status
) {
}
