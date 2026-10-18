package com.example.roshdeandishe.finance.shared;

public final class FinanceException extends RuntimeException {

    private final FinanceError error;

    public FinanceException(FinanceError error) {
        super(describe(error));
        this.error = error;
    }

    public FinanceError error() {
        return error;
    }

    private static String describe(FinanceError error) {
        return switch (error) {
            case FinanceError.TuitionFeeNotSet e ->
                    "برای مدرسه‌ی با شناسه " + e.schoolId() + " هنوز شهریه‌ای تنظیم نشده است.";
            case FinanceError.InvalidTuitionFee e -> e.reason();
            case FinanceError.InvalidAcademicYear e -> "سال تحصیلی «" + e.academicYear() + "» در فهرست سال‌ها نیست.";
            case FinanceError.UnattributedInvoicesExist e ->
                    "مدرسه‌ی " + e.schoolId() + " فاکتورِ بدون سال دارد؛ اول آن‌ها را به یک سال نسبت دهید.";
            case FinanceError.AttributionConflict e -> e.reason();
            case FinanceError.InvoiceNotFound e ->
                    "فاکتوری با شناسه " + e.invoiceId() + " در این مدرسه وجود ندارد.";
            case FinanceError.InvalidPaymentAmount e -> e.reason();
            case FinanceError.PaymentExceedsBalance e ->
                    "مانده‌ی قابل پرداخت فاکتور " + e.invoiceId() + " برابر " + e.remaining() + " است.";
            case FinanceError.InvalidDiscount e -> e.reason();
            case FinanceError.DiscountExceedsBalance e ->
                    "حداکثر تخفیف قابل ثبت برای فاکتور " + e.invoiceId() + " برابر " + e.maxDiscount() + " است.";
            case FinanceError.InvalidSetting e -> e.reason();
            case FinanceError.InvalidAuditFilter e -> e.reason();
            case FinanceError.InvalidFilter e -> e.reason();
            case FinanceError.InstallmentsNotEnabled e ->
                    "اقساط برای مدرسه‌ی " + e.schoolId() + " روشن نیست.";
            case FinanceError.InvalidInstallments e -> e.reason();
            case FinanceError.InstallmentsSumMismatch e ->
                    "جمعِ اقساطِ پرداخت‌نشده باید برابر " + e.expected() + " باشد.";
            case FinanceError.ExpenseNotFound e ->
                    "هزینه‌ای با شناسه " + e.expenseId() + " در این مدرسه وجود ندارد.";
            case FinanceError.ExpenseCategoryNotFound e ->
                    "دسته‌ای با شناسه " + e.categoryId() + " در این مدرسه وجود ندارد.";
            case FinanceError.VendorNotFound e ->
                    "طرف حسابی با شناسه " + e.vendorId() + " در این مدرسه وجود ندارد.";
            case FinanceError.InvalidExpense e -> e.reason();
            case FinanceError.ExpenseNotEditable e ->
                    "هزینه‌ی " + e.expenseId() + " در وضعیت " + e.status() + " قابل ویرایش نیست.";
            case FinanceError.InvalidExpenseStatusTransition e ->
                    "هزینه‌ی " + e.expenseId() + " را نمی‌توان از " + e.from() + " به " + e.to() + " برد.";
            case FinanceError.AttachmentNotFound e ->
                    "پیوستی با شناسه " + e.attachmentId() + " برای این هزینه وجود ندارد.";
            case FinanceError.InvalidAttachment e -> e.reason();
        };
    }
}
