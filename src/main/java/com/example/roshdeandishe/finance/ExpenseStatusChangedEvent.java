package com.example.roshdeandishe.finance;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * منتشر می‌شود با هر گذار چرخه‌ی حیات یک هزینه - ایجاد
 * ({@code previousStatus == null → DRAFT})، تأیید، پرداخت، لغو. عمداً یک
 * رویداد، نه چهار رکورد جدا: تنها مصرف‌کننده‌ی فعلی listener ثبت audit است
 * که با یک switch روی {@code newStatus} کد اقدام را می‌سازد؛ مصرف‌کننده‌ی
 * آینده‌ای که فقط PAID بخواهد، روی همین فیلد فیلتر می‌کند - همان انعطاف با
 * سطح عمومی کوچک‌تر.
 * <p>
 * ویرایش فیلدها در DRAFT عمداً رویداد/audit ندارد - فقط گذارهای چرخه‌ی حیات.
 */
public record ExpenseStatusChangedEvent(
        Long schoolId,
        Long expenseId,
        ExpenseStatus previousStatus,
        ExpenseStatus newStatus,
        BigDecimal amount,
        Long actorUserId,
        Instant occurredAt
) {
}
