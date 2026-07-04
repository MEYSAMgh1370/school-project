package com.example.roshdeandishe.finance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * تنها نقطه‌ی ورود عمومی به ماژول Finance.
 */
public interface FinanceFacade {

    /**
     * صدور فاکتور شهریه برای یک دانش‌آموز. مبلغ از شهریه‌ی مصوب مدرسه
     * ({@link #setTuitionFee}) خوانده می‌شود - دیگر پارامتر ورودی نیست،
     * چون قیمت‌گذاری مسئولیت داخلی همین ماژول است.
     * <p>
     * تصمیم صریح: اگر مدرسه هنوز شهریه‌ای تنظیم نکرده باشد، هیچ فاکتوری
     * صادر نمی‌شود (فقط یک warn لاگ می‌شود) و ثبت‌نام دانش‌آموز هرگز
     * مسدود نمی‌شود. دانش‌آموزانی که قبل از تنظیم شهریه ثبت‌نام شده‌اند
     * فاکتور عقب‌افتاده نمی‌گیرند - backfill وقتی ساخته می‌شود که واقعاً
     * لازم شود، نه زودتر.
     */
    void issueTuitionInvoice(Long schoolId, Long studentId);

    /**
     * تنظیم شهریه‌ی مصوب مدرسه (idempotent - ردیف موجود upsert می‌شود).
     *
     * @throws com.example.roshdeandishe.finance.shared.FinanceException با
     * FinanceError.InvalidTuitionFee اگر مبلغ null یا نامثبت باشد
     */
    void setTuitionFee(Long schoolId, BigDecimal amount);

    Optional<TuitionFeeInfo> getTuitionFee(Long schoolId);

    /**
     * فاکتورهای یک مدرسه، جدیدترین اول - برای صفحه/گزارش مالی ادمین.
     */
    List<InvoiceInfo> listInvoices(Long schoolId);

    record TuitionFeeInfo(Long schoolId, BigDecimal amount) {
    }

    record InvoiceInfo(Long id, Long schoolId, Long studentId, BigDecimal amount, Instant issuedAt) {
    }
}
