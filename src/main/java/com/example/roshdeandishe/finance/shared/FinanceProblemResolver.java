package com.example.roshdeandishe.finance.shared;

import com.example.roshdeandishe.platform.ProblemDetailFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * نگاشت FinanceError به ProblemDetail - وقتی هزینه‌ها اضافه شدند تعداد
 * caseها از حد «چند مورد ساده» گذشت و طبق CLAUDE.md §6 (الگوی
 * IamProblemResolver) به یک @Component مستقلِ قابل unit-test بدون MVC
 * جدا شد؛ FinanceExceptionHandler حالا فقط routing است.
 * پسوندهای type به همان قرارداد kebab-case قبلی می‌مانند تا
 * classifyError سمت کلاینت (finance.js/expenses.js) بدون تغییر کار کند.
 */
@Component
public class FinanceProblemResolver {

    public ProblemDetail resolve(FinanceError error) {
        return switch (error) {
            case FinanceError.TuitionFeeNotSet e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "tuition-fee-not-set",
                    "شهریه تنظیم نشده است",
                    "برای مدرسه‌ی با شناسه " + e.schoolId() + " هنوز شهریه‌ای تنظیم نشده است."
            );
            case FinanceError.InvalidAcademicYear e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-academic-year",
                    "سال تحصیلی نامعتبر است",
                    "سال تحصیلی «" + e.academicYear() + "» در فهرست سال‌ها نیست."
            );
            case FinanceError.UnattributedInvoicesExist e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "unattributed-invoices-exist",
                    "فاکتورِ بدون سال وجود دارد",
                    "اول اطلاعاتِ مالیِ قبلی را به یک سال نسبت دهید؛ وگرنه دانش‌آموزی که فاکتورِ قدیمی دارد دوباره فاکتور می‌گیرد."
            );
            case FinanceError.AttributionConflict e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "attribution-conflict",
                    "نسبت دادن به این سال ممکن نیست",
                    e.reason()
            );
            case FinanceError.InvalidTuitionFee e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-tuition-fee",
                    "مبلغ شهریه نامعتبر است",
                    e.reason()
            );
            case FinanceError.InvoiceNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "invoice-not-found",
                    "فاکتور یافت نشد",
                    "فاکتوری با شناسه " + e.invoiceId() + " در این مدرسه وجود ندارد."
            );
            case FinanceError.InvalidPaymentAmount e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-payment-amount",
                    "مبلغ پرداخت نامعتبر است",
                    e.reason()
            );
            case FinanceError.PaymentExceedsBalance e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "payment-exceeds-balance",
                    "پرداخت از مانده‌ی فاکتور بیشتر است",
                    "مانده‌ی قابل پرداخت فاکتور " + e.invoiceId() + " برابر " + e.remaining() + " است."
            );
            case FinanceError.InvalidDiscount e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-discount",
                    "تخفیف نامعتبر است",
                    e.reason()
            );
            case FinanceError.DiscountExceedsBalance e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "discount-exceeds-balance",
                    "تخفیف از مانده‌ی فاکتور بیشتر است",
                    "حداکثر تخفیف قابل ثبت برای فاکتور " + e.invoiceId() + " برابر " + e.maxDiscount()
                            + " است؛ مبلغ پرداخت‌شده را نمی‌شود تخفیف داد."
            );
            case FinanceError.InvalidAuditFilter e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-audit-filter",
                    "فیلتر گزارش تغییرات نامعتبر است",
                    e.reason()
            );
            case FinanceError.InvalidFilter e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-filter",
                    "فیلتر فهرست نامعتبر است",
                    e.reason()
            );
            case FinanceError.InvalidSetting e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-setting",
                    "مقدار تنظیم نامعتبر است",
                    e.reason()
            );
            case FinanceError.InstallmentsNotEnabled e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "installments-not-enabled",
                    "اقساط برای مدرسه روشن نیست",
                    "اول اقساط شهریه را در تنظیمات مالی روشن کنید."
            );
            case FinanceError.InvalidInstallments e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-installments",
                    "اقساط نامعتبر است",
                    e.reason()
            );
            case FinanceError.InstallmentsSumMismatch e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "installments-sum-mismatch",
                    "جمعِ اقساط با مانده برابر نیست",
                    "جمعِ مبلغِ اقساطِ پرداخت‌نشده باید دقیقاً برابر " + e.expected() + " باشد."
            );
            case FinanceError.ExpenseNotFound e ->ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "expense-not-found",
                    "هزینه یافت نشد",
                    "هزینه‌ای با شناسه " + e.expenseId() + " در این مدرسه وجود ندارد."
            );
            case FinanceError.ExpenseCategoryNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "expense-category-not-found",
                    "دسته‌ی هزینه یافت نشد",
                    "دسته‌ای با شناسه " + e.categoryId() + " در این مدرسه وجود ندارد."
            );
            case FinanceError.VendorNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "vendor-not-found",
                    "طرف حساب یافت نشد",
                    "طرف حسابی با شناسه " + e.vendorId() + " در این مدرسه وجود ندارد."
            );
            case FinanceError.InvalidExpense e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-expense",
                    "داده‌ی هزینه نامعتبر است",
                    e.reason()
            );
            case FinanceError.ExpenseNotEditable e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "expense-not-editable",
                    "هزینه قابل ویرایش نیست",
                    "هزینه‌ی " + e.expenseId() + " در وضعیت " + e.status() +
                            " است - فقط پیش‌نویس (DRAFT) قابل ویرایش است."
            );
            case FinanceError.InvalidExpenseStatusTransition e -> ProblemDetailFactory.problem(
                    HttpStatus.CONFLICT, "invalid-expense-status-transition",
                    "گذار وضعیت هزینه نامعتبر است",
                    "هزینه‌ی " + e.expenseId() + " را نمی‌توان از " + e.from() + " به " + e.to() + " برد."
            );
            case FinanceError.AttachmentNotFound e -> ProblemDetailFactory.problem(
                    HttpStatus.NOT_FOUND, "attachment-not-found",
                    "پیوست یافت نشد",
                    "پیوستی با شناسه " + e.attachmentId() + " برای این هزینه وجود ندارد."
            );
            case FinanceError.InvalidAttachment e -> ProblemDetailFactory.problem(
                    HttpStatus.BAD_REQUEST, "invalid-attachment",
                    "فایل پیوست نامعتبر است",
                    e.reason()
            );
        };
    }
}
