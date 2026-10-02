package com.example.roshdeandishe.web.dto;

/**
 * یک فرزندِ لینک‌شده‌ی والد برای بخش «فرزندان من» داشبورد.
 * <p>
 * {@code className}/{@code section} ممکن است null باشند (دانش‌آموز هنوز در
 * کلاسی ثبت‌نام نشده یا کلاسش پیدا نشد) - template آن‌ها را شرطی نشان می‌دهد.
 * وضعیت مالی عمداً فقط شمارش فاکتورها بر اساس وضعیتشان است، نه مبلغ: واحد پولی
 * هنوز تصمیم‌نگرفته است (OpenSpec: changes/finance-monetary-unit.md) و
 * شمارش‌ها همان وضعیت‌هایی‌اند که /my-invoices نشان می‌دهد.
 *
 * @param studentId    شناسه‌ی دانش‌آموز؛ فقط در لینک‌های «نمره‌ها»/«فاکتورها» می‌آید (نام فرزند عمداً در
 *                     URL نمی‌رود)
 * @param schoolId     مدرسه‌ی همین فرزند؛ لینک آن را به‌عنوان مدرسه‌ی انتخاب‌شده ذخیره می‌کند
 * @param invoiceCount همه‌ی فاکتورهای این دانش‌آموز
 * @param overdueCount فاکتورهای دیرکرد (OVERDUE)
 * @param openCount    فاکتورهای باز و سررسید‌نگذشته (UNPAID و PARTIAL)
 */
public record WardRow(Long studentId, Long schoolId, String name, String schoolName, String className, String section,
                      int invoiceCount, int overdueCount, int openCount) {

    public boolean hasClass() {
        return className != null && !className.isBlank();
    }

    public boolean hasSection() {
        return section != null && !section.isBlank();
    }

    public boolean hasNoInvoices() {
        return invoiceCount == 0;
    }

    /** دیرکرد بر هر وضعیت دیگری مقدم است (همان قاعده‌ی InvoiceStatus.OVERDUE). */
    public boolean isOverdue() {
        return overdueCount > 0;
    }

    public boolean isOpen() {
        return overdueCount == 0 && openCount > 0;
    }

    public boolean isSettled() {
        return invoiceCount > 0 && overdueCount == 0 && openCount == 0;
    }
}
