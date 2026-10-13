package com.example.roshdeandishe.finance;

import java.time.Instant;

/**
 * منتشر می‌شود هر بار که طرحِ اقساطِ یک فاکتور ساخته/بازشمارش یا ویرایش می‌شود
 * ({@code FinanceFacade.generateInstallments/updateInstallments}) - همان الگوی InvoiceDiscountedEvent؛
 * مصرف‌کننده‌ی فعلی listener ثبت audit همین ماژول است. {@code count} تعداد اقساطِ طرح پس از تغییر،
 * {@code actorUserId} nullable است.
 */
public record InstallmentsChangedEvent(
        Long schoolId,
        Long invoiceId,
        Long studentId,
        int count,
        Long actorUserId,
        Instant occurredAt
) {
}
