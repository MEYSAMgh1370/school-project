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

    /**
     * فاکتورهای زیرمجموعه‌ای از دانش‌آموزانِ یک مدرسه - پشتیبان endpoint
     * خودسرویس {@code GET /api/me/schools/{schoolId}/invoices}
     * (MyInvoicesController): ترکیبِ «این کاربر کدام دانش‌آموزهاست/ولیِ
     * کدام‌هاست» در کنترلر و از روی AcademicFacade انجام می‌شود، این متد
     * فقط داده‌ی مالی را برمی‌گرداند. لیست خالی برای مجموعه‌ی خالی.
     */
    List<InvoiceInfo> listInvoicesForStudents(Long schoolId, List<Long> studentIds);

    /**
     * ثبت دستی یک پرداخت روی یک فاکتور (ادمین/مدیر مالی - هیچ درگاه
     * آنلاینی در کار نیست). پرداخت جزئی/قسطی مجاز است؛ جمع پرداخت‌ها روی
     * InvoiceEntity.amountPaid تراکنشی جلو می‌رود و وضعیت فاکتور از همان
     * مشتق می‌شود.
     *
     * @throws com.example.roshdeandishe.finance.shared.FinanceException با
     * InvoiceNotFound اگر فاکتور در این مدرسه نباشد، InvalidPaymentAmount
     * برای مبلغ null/نامثبت، PaymentExceedsBalance اگر از مانده بیشتر شود
     * (اضافه‌پرداخت/بستانکاری عمداً پشتیبانی نمی‌شود).
     */
    PaymentInfo recordPayment(Long schoolId, Long invoiceId, BigDecimal amount, PaymentMethod method, String note);

    /**
     * پرداخت‌های یک فاکتور، قدیمی‌ترین اول (ترتیب وقوع).
     */
    List<PaymentInfo> listPayments(Long schoolId, Long invoiceId);

    /**
     * صدور فاکتور برای دانش‌آموزانی که هیچ فاکتوری ندارند (مثلاً قبل از
     * تنظیم شهریه ثبت‌نام شده‌اند) - به مبلغ شهریه‌ی *فعلی* مدرسه، نه نرخ
     * تاریخی (تاریخچه‌ی شهریه نگه داشته نمی‌شود). Idempotent: اجرای دوباره
     * چیزی صادر نمی‌کند. برخلاف issueTuitionInvoice که بدون شهریه فقط warn
     * می‌کند، اینجا TuitionFeeNotSet پرتاب می‌شود - این یک اقدام صریح ادمین
     * است و سکوت گمراه‌کننده بود.
     *
     * @return تعداد فاکتورهای صادرشده
     */
    int backfillInvoices(Long schoolId);

    record TuitionFeeInfo(Long schoolId, BigDecimal amount) {
    }

    record InvoiceInfo(Long id, Long schoolId, Long studentId, BigDecimal amount, BigDecimal amountPaid,
                       Instant issuedAt, Instant dueDate, InvoiceStatus status) {
    }

    record PaymentInfo(Long id, Long invoiceId, BigDecimal amount, PaymentMethod method, String note,
                       Long recordedByUserId, Instant paidAt) {
    }
}
