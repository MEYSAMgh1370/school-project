package com.example.roshdeandishe.finance;

/**
 * وضعیت یک فاکتور - عمداً **هیچ‌وقت persist نمی‌شود** و در لحظه‌ی خواندن از
 * روی (amount, amountPaid, dueDate) مشتق می‌شود (رجوع به
 * FinanceFacadeImpl#deriveStatus): یک ستون status جداگانه می‌توانست از
 * واقعیت پرداخت‌ها عقب بماند (مثلاً OVERDUE شدن با گذر زمان بدون هیچ
 * mutation ای اتفاق می‌افتد) - یک منبع حقیقت، نه دو تا.
 * <p>
 * ترتیبِ تعریفِ ثابت‌ها از بهترین به بدترین است و FinanceFacadeImpl#toStudentRow برای «بدترین
 * وضعیتِ فاکتورهای جاری» به آن تکیه می‌کند؛ ثابت جدید را سر جای درستش اضافه کنید.
 * <p>
 * در پکیج root (نه internal) چون هم رکوردهای FinanceFacade و هم
 * پاسخ‌های REST به آن نیاز دارند.
 */
public enum InvoiceStatus {

    /** کامل پرداخت شده (amountPaid >= amount) - وضعیت نهایی. */
    PAID,

    /** بخشی پرداخت شده و هنوز از سررسید نگذشته. */
    PARTIAL,

    /** هیچ پرداختی نشده و هنوز از سررسید نگذشته. */
    UNPAID,

    /**
     * از سررسید گذشته و هنوز کامل پرداخت نشده - پرداختِ جزئیِ دیرشده هم
     * OVERDUE است، نه PARTIAL: از دید مدیر مالی «دیرکرد» صفت غالب است.
     */
    OVERDUE
}
