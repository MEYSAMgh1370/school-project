package com.example.roshdeandishe.finance;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * منتشر می‌شود هر بار که {@code FinanceFacade.issueTuitionInvoice} فراخوانی
 * می‌شود. تنها مصرف‌کننده‌ی فعلی آن listener ثبت audit در همین ماژول است،
 * اما چون در پکیج بیرونی (نه internal) قرار دارد، در آینده هر ماژول دیگری
 * (مثلاً notification) هم می‌تواند بدون تغییر این رویداد به آن subscribe شود.
 * <p>
 * {@code actorUserId} می‌تواند null باشد (مثلاً وقتی این متد از یک
 * event listener داخلی مثل StudentEnrolledEventListener فراخوانی می‌شود و
 * کاربر احراز هویت‌شده‌ای در کار نیست، نه از یک درخواست HTTP مستقیم).
 */
public record InvoiceIssuedEvent(
        Long schoolId,
        Long studentId,
        BigDecimal amount,
        Long actorUserId,
        Instant occurredAt
) {
}
