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
     * تنظیم شهریه‌ی مصوبِ یک سالِ تحصیلی (idempotent - ردیف موجودِ همان سال upsert می‌شود). سالِ
     * خالی = سالِ جاریِ مدرسه و اگر مدرسه سالِ جاری ندارد، «بدون سال» (رفتارِ پیش از فاز ۲).
     *
     * @throws com.example.roshdeandishe.finance.shared.FinanceException با
     * FinanceError.InvalidTuitionFee اگر مبلغ null یا نامثبت باشد، InvalidAcademicYear اگر سال
     * در فهرست نباشد
     */
    void setTuitionFee(Long schoolId, String academicYear, BigDecimal amount);

    /** شهریه‌ی یک سال (سالِ خالی مثل {@link #setTuitionFee}). */
    Optional<TuitionFeeInfo> getTuitionFee(Long schoolId, String academicYear);

    /** شهریه‌ی سالِ جاری (مدرسه‌ای که سالِ جاری ندارد: «بدون سال»). */
    void setTuitionFee(Long schoolId, BigDecimal amount);

    /** شهریه‌ی سالِ جاری (مدرسه‌ای که سالِ جاری ندارد: «بدون سال»). */
    Optional<TuitionFeeInfo> getTuitionFee(Long schoolId);

    /** صدورِ فاکتورهای سالِ جاری ({@link #backfillInvoices(Long, String)} با سالِ خالی). */
    int backfillInvoices(Long schoolId);

    /**
     * همه‌ی فاکتورهای یک مدرسه در همه‌ی سال‌ها، جدیدترین اول (گزارش‌ها).
     */
    List<InvoiceInfo> listInvoices(Long schoolId);

    /**
     * فاکتورهای یک سال به‌علاوه‌ی فاکتورهای «بدون سال» (تا نسبت داده شوند)، جدیدترین اول - برای صفحه‌ی
     * مالیِ ادمین. سالِ خالی = سالِ جاری؛ مدرسه‌ای که سالِ جاری ندارد همه را می‌گیرد.
     */
    List<InvoiceInfo> listInvoices(Long schoolId, String academicYear);

    /**
     * فاکتورهای زیرمجموعه‌ای از دانش‌آموزانِ یک مدرسه، آن‌گونه که خانواده می‌بیند - پشتیبان endpoint
     * خودسرویس {@code GET /api/me/schools/{schoolId}/invoices}: سالِ جاری، فاکتورهای «بدون سال»، و
     * فاکتورِ سال‌های <b>قبل</b> که هنوز مانده دارد (بدهی پنهان نمی‌ماند)؛ فاکتورِ تسویه‌شده‌ی سال‌های قبل
     * پنهان است (academic-year.md تصمیم ۵) و مدرسه‌ای که سالِ جاری ندارد محدود نمی‌شود. ترکیبِ «این کاربر کدام دانش‌آموزهاست/ولیِ کدام‌هاست» در کنترلر و از روی
     * AcademicFacade انجام می‌شود. لیست خالی برای مجموعه‌ی خالی.
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
     * تخفیف روی فاکتور (از ابتدا یا بخشودنِ بخشی از مانده)؛ مبلغ قابل پرداخت = amount - discountTotal.
     * مستقل از تنظیمِ اقساط کار می‌کند و append-only است.
     *
     * @throws com.example.roshdeandishe.finance.shared.FinanceException با InvoiceNotFound،
     * InvalidDiscount برای مبلغ null/نامثبت یا توضیحِ بلندتر از ۲۵۵ نویسه، DiscountExceedsBalance اگر
     * قابل پرداخت از مبلغِ پرداخت‌شده کمتر شود
     */
    DiscountInfo applyDiscount(Long schoolId, Long invoiceId, BigDecimal amount, String reason);

    /**
     * همان، با مهلتِ اختیاری (تخفیفِ مشروط): تخفیف از همین لحظه قابل پرداخت را کم می‌کند، ولی فقط اگر فاکتور
     * تا پایانِ روزِ {@code deadline} (به وقتِ مدرسه) کاملاً تسویه شود می‌ماند؛ وگرنه خودکار برداشته می‌شود
     * (LAPSED). {@code deadline} خالی = تخفیفِ بی‌قیدوشرط. InvalidDiscount اگر مهلت پیش از امروزِ مدرسه باشد.
     */
    DiscountInfo applyDiscount(Long schoolId, Long invoiceId, BigDecimal amount, String reason,
                               java.time.LocalDate deadline);

    /** تخفیف‌های یک فاکتور، قدیمی‌ترین اول. */
    List<DiscountInfo> listDiscounts(Long schoolId, Long invoiceId);

    /**
     * جمعِ تخفیف‌های مشروطِ بازِ (PENDING) چند فاکتور و زودترین مهلتشان با <b>یک</b> query (پاسخِ خودسرویس؛
     * بدون N+1). فاکتورِ بدونِ تخفیفِ باز در نتیجه کلید ندارد.
     */
    java.util.Map<Long, PendingDiscountSummary> pendingDiscountsForInvoices(
            Long schoolId, java.util.Collection<Long> invoiceIds);

    /**
     * جاروی تخفیف‌های مشروطِ مهلت‌گذشته (کارِ ساعتیِ ExpiredDiscountSweeper): هر فاکتور در تراکنشِ خودش و زیر
     * قفل؛ شکستِ یک فاکتور بقیه را متوقف نمی‌کند. Idempotent.
     *
     * @return تعداد تخفیف‌های LAPSEDشده
     */
    int sweepExpiredDiscounts();

    /** اقساطِ یک فاکتور به ترتیب (سررسید، seq)؛ خالی = فاکتور طرحِ اقساط ندارد. InvoiceNotFound اگر فاکتور نباشد. */
    List<InstallmentInfo> listInstallments(Long schoolId, Long invoiceId);

    /**
     * اقساطِ چند فاکتورِ یک مدرسه با <b>یک</b> query (فهرست‌ها و endpoint خودسرویس؛ بدون N+1).
     * فاکتورِ بی‌قسط در نتیجه کلید ندارد. شناسه‌هایی که متعلق به این مدرسه نیستند نادیده گرفته می‌شوند.
     */
    java.util.Map<Long, List<InstallmentInfo>> listInstallmentsForInvoices(
            Long schoolId, java.util.Collection<Long> invoiceIds);

    /**
     * ساخت یا بازشمارشِ طرحِ اقساط (tuition-installments.md): اقساطِ کاملاً پرداخت‌شده می‌مانند و
     * مانده‌ی قابل پرداخت (قابل پرداخت منهای آن‌ها) به {@code count} قسطِ پرداخت‌نشده‌ی مساوی (قسطِ آخر
     * باقی‌مانده‌ی گرد کردن را می‌گیرد) با سررسیدهای ماهِ شمسیِ {@code intervalMonths}-تایی از
     * {@code firstDueDate} تقسیم می‌شود. اگر چیزی نمانده باشد {@code count} باید صفر باشد.
     *
     * @throws com.example.roshdeandishe.finance.shared.FinanceException با InvoiceNotFound،
     * InstallmentsNotEnabled اگر مدرسه اقساط را روشن نکرده، InvalidInstallments برای تعداد (۱ تا ۶۰)،
     * فاصله (۱ تا ۱۲)، تاریخِ خالی یا سهمِ کمتر از ۰٫۰۱
     */
    List<InstallmentInfo> generateInstallments(Long schoolId, Long invoiceId, int count,
                                               java.time.LocalDate firstDueDate, int intervalMonths);

    /**
     * ویرایشِ دستیِ مبلغ و سررسیدِ اقساطِ پرداخت‌نشده. {@code items} باید دقیقاً همه‌ی اقساطِ پرداخت‌نشده‌ی
     * فاکتور باشد (افزودن/حذف ممکن نیست) و جمعِ مبلغ‌ها برابر مانده‌ی قابل تقسیم.
     *
     * @throws com.example.roshdeandishe.finance.shared.FinanceException با InvoiceNotFound،
     * InstallmentsNotEnabled، InvalidInstallments (قسطِ پرداخت‌شده/ناشناخته/جاافتاده/مبلغ نامثبت)،
     * InstallmentsSumMismatch
     */
    List<InstallmentInfo> updateInstallments(Long schoolId, Long invoiceId, List<InstallmentEdit> items);

    /**
     * «صدورِ فاکتورهای سال»: برای هر دانش‌آموزِ فعلیِ مدرسه که برای این سال فاکتور ندارد، به مبلغ شهریه‌ی
     * <i>همان سال</i> فاکتور می‌دهد (سالِ خالی مثل {@link #setTuitionFee}). Idempotent. برخلاف
     * issueTuitionInvoice که بدون شهریه فقط warn می‌کند، اینجا TuitionFeeNotSet پرتاب می‌شود - اقدامِ
     * صریح ادمین است. اگر برای یک سالِ مشخص فاکتورِ «بدون سال» هست، UnattributedInvoicesExist (اول
     * {@link #attributeToYear}): وگرنه دانش‌آموزِ دارای فاکتورِ قدیمی دوباره فاکتور می‌گرفت.
     *
     * @return تعداد فاکتورهای صادرشده
     */
    int backfillInvoices(Long schoolId, String academicYear);

    /**
     * نسبت دادنِ شهریه و فاکتورهای «بدون سال» به یک سالِ فهرست‌شده، در یک تراکنش (تصمیمِ مدیر برای داده‌ی
     * موجود، academic-year.md فاز ۲). چیزی «بدون سال» نبود: هیچ تغییری. AttributionConflict اگر آن سال
     * از قبل شهریه/فاکتورِ همان دانش‌آموز را دارد؛ InvalidAcademicYear اگر سال در فهرست نباشد.
     */
    AttributionResult attributeToYear(Long schoolId, String academicYear);

    /** آنچه هنوز به سالی نسبت داده نشده - برای هشدارِ صفحه‌ی مالی. */
    UnattributedSummary unattributedSummary(Long schoolId);

    record AttributionResult(boolean tuitionAttributed, int invoicesAttributed) {
    }

    record UnattributedSummary(boolean tuitionFee, int invoices) {
    }

    record TuitionFeeInfo(Long schoolId, BigDecimal amount, String academicYear) {
    }

    record InvoiceInfo(Long id, Long schoolId, Long studentId, BigDecimal amount, BigDecimal amountPaid,
                       Instant issuedAt, Instant dueDate, InvoiceStatus status, String academicYear,
                       BigDecimal discountTotal) {
    }

    record DiscountInfo(Long id, Long invoiceId, BigDecimal amount, String reason,
                        Long createdByUserId, Instant createdAt,
                        java.time.LocalDate deadline, DiscountStatus status, Instant resolvedAt) {
    }

    /** تخفیف‌های مشروطِ بازِ یک فاکتور: جمعِ مبلغ و زودترین مهلت. */
    record PendingDiscountSummary(BigDecimal amount, java.time.LocalDate earliestDeadline) {
    }

    record InstallmentInfo(Long id, Long invoiceId, int seq, BigDecimal amount, BigDecimal amountPaid,
                           java.time.LocalDate dueDate, InstallmentStatus status) {
    }

    record InstallmentEdit(Long id, BigDecimal amount, java.time.LocalDate dueDate) {
    }

    record PaymentInfo(Long id, Long invoiceId, BigDecimal amount, PaymentMethod method, String note,
                       Long recordedByUserId, Instant paidAt) {
    }
}
