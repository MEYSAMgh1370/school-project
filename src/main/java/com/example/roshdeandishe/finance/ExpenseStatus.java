package com.example.roshdeandishe.finance;

/**
 * چرخه‌ی حیات یک هزینه - برخلاف InvoiceStatus این وضعیت واقعاً persist
 * می‌شود، چون گذارها اقدام‌های صریح انسانی‌اند (تأیید/پرداخت/لغو)، نه
 * مشتق‌شدنی از داده‌ی دیگر.
 * <p>
 * ماشین حالت (ExpenseFacadeImpl#transition): DRAFT→APPROVED (فقط
 * EXPENSE_APPROVE)، APPROVED→PAID و DRAFT|APPROVED→CANCELLED (هر دو
 * EXPENSE_MANAGE). PAID و CANCELLED نهایی‌اند. ویرایش فیلدها فقط در DRAFT.
 * تأیید چندمرحله‌ای عمداً ساخته نشده (سند طراحی آن را به آینده موکول کرده).
 * <p>
 * در پکیج root (نه internal) چون Entity داخلی، رکوردهای ExpenseFacade،
 * رویداد و کنترلر همگی به آن نیاز دارند - internal تایپ‌های root را می‌بیند،
 * برعکسش نه.
 */
public enum ExpenseStatus {

    /** ثبت اولیه - هنوز قابل ویرایش. */
    DRAFT,

    /** تأییدشده (EXPENSE_APPROVE) - در گزارش‌ها حساب می‌شود. */
    APPROVED,

    /** پرداخت‌شده - نهایی. */
    PAID,

    /** لغوشده - نهایی؛ جایگزین حذف فیزیکی (هزینه هرگز hard-delete نمی‌شود). */
    CANCELLED
}
