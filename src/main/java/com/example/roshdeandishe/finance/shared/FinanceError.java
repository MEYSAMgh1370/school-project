package com.example.roshdeandishe.finance.shared;

/**
 * مدل خطای دامنه‌محور مختص ماژول finance.
 */
public sealed interface FinanceError {

    record TuitionFeeNotSet(Long schoolId) implements FinanceError {
    }

    record InvalidTuitionFee(String reason) implements FinanceError {
    }

    /** کدِ سال تحصیلی در فهرستِ سال‌ها نیست. */
    record InvalidAcademicYear(String academicYear) implements FinanceError {
    }

    /**
     * صدورِ فاکتورهای یک سال وقتی هنوز فاکتورِ «بدون سال» هست: دانش‌آموزی که فاکتورِ قدیمی دارد دوباره
     * فاکتور می‌گرفت. اول باید اطلاعاتِ قبلی به یک سال نسبت داده شود.
     */
    record UnattributedInvoicesExist(Long schoolId) implements FinanceError {
    }

    /** نسبت دادنِ اطلاعاتِ قبلی به یک سال با داده‌ی همان سال تداخل دارد (شهریه یا فاکتورِ تکراری). */
    record AttributionConflict(String academicYear, String reason) implements FinanceError {
    }

    /**
     * فاکتوری با این شناسه در این مدرسه وجود ندارد - عمداً برای شناسه‌ای که
     * متعلق به مدرسه‌ی دیگری است هم همین برمی‌گردد (وجود فاکتور مدرسه‌ی
     * دیگر را لو نمی‌دهیم).
     */
    record InvoiceNotFound(Long invoiceId) implements FinanceError {
    }

    record InvalidPaymentAmount(String reason) implements FinanceError {
    }

    /**
     * پرداخت از مانده‌ی فاکتور بیشتر می‌شود - تصمیم صریح: بستانکاری/اعتبار
     * نگه نمی‌داریم؛ اضافه‌پرداخت رد می‌شود، نه ذخیره.
     */
    record PaymentExceedsBalance(Long invoiceId, java.math.BigDecimal remaining) implements FinanceError {
    }

    /** مبلغ تخفیف null/نامثبت یا توضیحِ آن بلندتر از سقف است. */
    record InvalidDiscount(String reason) implements FinanceError {
    }

    /** تخفیف مبلغ قابل پرداخت را از آنچه تاکنون پرداخت شده کمتر می‌کند؛ maxDiscount = حداکثر تخفیفِ مجاز. */
    record DiscountExceedsBalance(Long invoiceId, java.math.BigDecimal maxDiscount) implements FinanceError {
    }

    /** مقدارِ تنظیمِ مالی (مثلاً روشن/خاموشِ اقساط) خالی یا نامعتبر است. */
    record InvalidSetting(String reason) implements FinanceError {
    }

    /** ساخت/ویرایشِ طرحِ اقساط وقتی مدرسه اقساط را روشن نکرده است. */
    record InstallmentsNotEnabled(Long schoolId) implements FinanceError {
    }

    /** ورودیِ اقساط نامعتبر است: تعداد/تاریخ/مبلغ، قسطِ پرداخت‌شده یا ناشناخته، یا فهرستِ ناقص/اضافه. */
    record InvalidInstallments(String reason) implements FinanceError {
    }

    /** جمعِ مبلغِ اقساطِ پرداخت‌نشده با مانده‌ی قابل تقسیم برابر نیست؛ expected = جمعِ لازم. */
    record InstallmentsSumMismatch(java.math.BigDecimal expected) implements FinanceError {
    }

    /**
     * هزینه/دسته/طرف حساب متعلق به مدرسه‌ی دیگر هم NotFound است، نه
     * Forbidden - وجود رکورد مدرسه‌ی دیگر را لو نمی‌دهیم (همان قرارداد
     * InvoiceNotFound).
     */
    record ExpenseNotFound(Long expenseId) implements FinanceError {
    }

    record ExpenseCategoryNotFound(Long categoryId) implements FinanceError {
    }

    record VendorNotFound(Long vendorId) implements FinanceError {
    }

    /** داده‌ی نامعتبر هزینه: مبلغ نامثبت، دسته‌ی غایب/غیرفعال، تاریخ خالی... */
    record InvalidExpense(String reason) implements FinanceError {
    }

    /** ویرایش فیلدها فقط در DRAFT مجاز است (رجوع به ExpenseStatus). */
    record ExpenseNotEditable(Long expenseId,
                              com.example.roshdeandishe.finance.ExpenseStatus status) implements FinanceError {
    }

    record InvalidExpenseStatusTransition(Long expenseId,
                                          com.example.roshdeandishe.finance.ExpenseStatus from,
                                          com.example.roshdeandishe.finance.ExpenseStatus to) implements FinanceError {
    }

    record AttachmentNotFound(Long attachmentId) implements FinanceError {
    }

    /** فایل خالی، بزرگ‌تر از سقف، یا بدون نام - سقف دامنه‌ای، نه servlet. */
    record InvalidAttachment(String reason) implements FinanceError {
    }
}
