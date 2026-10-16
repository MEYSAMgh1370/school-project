package com.example.roshdeandishe.finance;

/**
 * وضعیتِ یک قسط؛ مثل {@link InvoiceStatus} هرگز persist نمی‌شود و هنگام خواندن مشتق می‌شود:
 * PAID = کاملاً پرداخت‌شده؛ در غیر این صورت OVERDUE اگر سررسیدش (به وقتِ مدرسه) گذشته؛
 * وگرنه PARTIAL اگر بخشی پرداخت شده و UNPAID اگر هیچ.
 */
public enum InstallmentStatus {
    PAID, PARTIAL, UNPAID, OVERDUE
}
