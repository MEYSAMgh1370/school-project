package com.example.roshdeandishe.web.model;

import com.example.roshdeandishe.studentview.StudentSummary;
import com.example.roshdeandishe.studentview.TodayAttendance;

/**
 * یک فرزندِ لینک‌شده‌ی والد برای بخش «فرزندان من» پیشخوان.
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
 * @param summary      خلاصهٔ نمره‌ها/آزمون‌ها/غیبتِ همین فرزند (تهی = نشان داده نمی‌شود)
 * @param attendance   وضعیتِ حضور و غیابِ امروزِ همین فرزند (تهی = {@link TodayAttendance#UNAVAILABLE})
 */
public record WardRow(Long studentId, Long schoolId, String name, String schoolName, String className, String section,
                      int invoiceCount, int overdueCount, int openCount, TodayAttendance attendance,
                      StudentSummary summary) {

    public WardRow {
        attendance = attendance == null ? TodayAttendance.UNAVAILABLE : attendance;
    }

    /** بدونِ خلاصهٔ نمره/آزمون/غیبت (تست‌های قدیمی). */
    public WardRow(Long studentId, Long schoolId, String name, String schoolName, String className, String section,
                   int invoiceCount, int overdueCount, int openCount, TodayAttendance attendance) {
        this(studentId, schoolId, name, schoolName, className, section, invoiceCount, overdueCount, openCount,
                attendance, null);
    }

    public boolean hasSummary() {
        return summary != null;
    }

    /** بدونِ وضعیتِ حضور و غیاب (مدرسه‌ی بدونِ آن ماژول و تست‌های قدیمی). */
    public WardRow(Long studentId, Long schoolId, String name, String schoolName, String className, String section,
                   int invoiceCount, int overdueCount, int openCount) {
        this(studentId, schoolId, name, schoolName, className, section, invoiceCount, overdueCount, openCount,
                TodayAttendance.UNAVAILABLE);
    }

    public boolean hasAttendance() {
        return attendance != TodayAttendance.UNAVAILABLE;
    }

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
