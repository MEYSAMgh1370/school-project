package com.example.roshdeandishe.finance;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * منتشر می‌شود هر بار که یک پرداخت روی یک فاکتور ثبت می‌شود
 * ({@code FinanceFacade.recordPayment}) - همان الگوی InvoiceIssuedEvent:
 * در پکیج بیرونی تا مصرف‌کننده‌ی فعلی (listener ثبت audit همین ماژول) و هر
 * مصرف‌کننده‌ی آینده (مثلاً notification برای اطلاع‌رسانی پرداخت) بدون تغییر این
 * رویداد به آن subscribe شوند.
 * <p>
 * {@code actorUserId} مثل InvoiceIssuedEvent nullable است، هرچند در عمل
 * ثبت پرداخت فعلاً همیشه از یک درخواست HTTP احراز هویت‌شده می‌آید.
 */
public record PaymentRecordedEvent(
        Long schoolId,
        Long invoiceId,
        Long studentId,
        BigDecimal amount,
        Long actorUserId,
        Instant occurredAt
) {
}
