package com.example.roshdeandishe.finance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * دومین نقطه‌ی ورود عمومیِ ماژول finance، جدا از {@link FinanceFacade}
 * (شهریه/فاکتور/پرداخت) - نه رشد دادن آن به یک رابط ~۲۰ متدیِ آش‌شله‌قلمکار.
 * از نظر Spring Modulith هر دو رابط عمومیِ همین ماژول‌اند (دقیقاً مثل
 * FinanceFacade + FinanceController + InvoiceIssuedEvent که همین الان هم
 * چند نوع عمومی در ریشه‌ی این ماژول‌اند)، فقط مسئولیتشان جداست: این یکی
 * هزینه‌های مدرسه (نه درآمد آن) را مدیریت می‌کند.
 */
public interface ExpenseFacade {

    List<CategoryInfo> listCategories(Long schoolId);

    CategoryInfo createCategory(Long schoolId, String name);

    CategoryInfo updateCategory(Long schoolId, Long categoryId, String name, Boolean active);

    /**
     * دسته‌های استاندارد فارسی را برای این مدرسه می‌سازد (حقوق و دستمزد،
     * قبوض، ...) - idempotent بر اساس نام: دسته‌ی از قبل موجود دوباره ساخته
     * نمی‌شود. اجرای چندباره فقط دسته‌های تازه را اضافه می‌کند.
     *
     * @return تعداد دسته‌ی تازه‌ساخته‌شده
     */
    int seedDefaultCategories(Long schoolId);

    List<VendorInfo> listVendors(Long schoolId);

    VendorInfo createVendor(Long schoolId, String name, String phone, String nationalId,
                            String economicCode, String address);

    VendorInfo updateVendor(Long schoolId, Long vendorId, String name, String phone,
                            String nationalId, String economicCode, String address, Boolean active);

    /**
     * ثبت یک هزینه‌ی تازه به‌عنوان DRAFT.
     *
     * @throws com.example.roshdeandishe.finance.shared.FinanceException با
     * InvalidExpense برای مبلغ نامثبت/تاریخ خالی، ExpenseCategoryNotFound
     * اگر دسته در این مدرسه نباشد، VendorNotFound اگر vendorId داده شده و
     * در این مدرسه نباشد.
     */
    ExpenseInfo createExpense(Long schoolId, LocalDate expenseDate, BigDecimal amount, Long categoryId,
                              Long vendorId, PaymentMethod paymentMethod, String description,
                              String receiptNumber, String invoiceNumber, String documentNumber);

    /**
     * ویرایش فیلدهای یک هزینه - فقط در وضعیت DRAFT (وگرنه ExpenseNotEditable).
     */
    ExpenseInfo updateExpense(Long schoolId, Long expenseId, LocalDate expenseDate, BigDecimal amount,
                             Long categoryId, Long vendorId, PaymentMethod paymentMethod, String description,
                             String receiptNumber, String invoiceNumber, String documentNumber);

    List<ExpenseInfo> listExpenses(Long schoolId, LocalDate from, LocalDate to, Long categoryId,
                                   Long vendorId, ExpenseStatus status, PaymentMethod paymentMethod);

    ExpenseInfo getExpense(Long schoolId, Long expenseId);

    /** DRAFT → APPROVED. */
    ExpenseInfo approveExpense(Long schoolId, Long expenseId);

    /** APPROVED → PAID (نهایی). */
    ExpenseInfo payExpense(Long schoolId, Long expenseId);

    /** DRAFT|APPROVED → CANCELLED (نهایی). */
    ExpenseInfo cancelExpense(Long schoolId, Long expenseId);

    /**
     * افزودن پیوست - در هر وضعیتی جز CANCELLED مجاز (رسیدها معمولاً بعد از
     * پرداخت هم می‌رسند؛ قفلِ ویرایش‌فقط‌در-DRAFT شامل پیوست نمی‌شود).
     *
     * @throws com.example.roshdeandishe.finance.shared.FinanceException با
     * InvalidAttachment برای فایل خالی/بزرگ‌تر از سقف/بدون نام.
     */
    AttachmentInfo addAttachment(Long schoolId, Long expenseId, String fileName, String contentType,
                                 byte[] content);

    List<AttachmentInfo> listAttachments(Long schoolId, Long expenseId);

    AttachmentContent loadAttachmentContent(Long schoolId, Long expenseId, Long attachmentId);

    void deleteAttachment(Long schoolId, Long expenseId, Long attachmentId);

    List<CategorySummary> summaryByCategory(Long schoolId, LocalDate from, LocalDate to);

    List<VendorSummary> summaryByVendor(Long schoolId, LocalDate from, LocalDate to);

    List<MethodSummary> summaryByMethod(Long schoolId, LocalDate from, LocalDate to);

    /**
     * درآمد (جمع پرداخت‌های شهریه در بازه - FinanceFacade.PaymentInfo) در
     * برابر هزینه (همان قاعده‌ی ثابتِ APPROVED+PAID) - یک سود/زیانِ ساده.
     */
    FinanceSummary financeSummary(Long schoolId, LocalDate from, LocalDate to);

    record CategoryInfo(Long id, Long schoolId, String name, boolean active) {
    }

    record VendorInfo(Long id, Long schoolId, String name, String phone, String nationalId,
                      String economicCode, String address, boolean active) {
    }

    record ExpenseInfo(Long id, Long schoolId, LocalDate expenseDate, BigDecimal amount, Long categoryId,
                       Long vendorId, PaymentMethod paymentMethod, ExpenseStatus status, String description,
                       String receiptNumber, String invoiceNumber, String documentNumber,
                       Long createdByUserId, Instant createdAt, Instant updatedAt) {
    }

    record AttachmentInfo(Long id, Long expenseId, String fileName, String contentType,
                          long sizeBytes, Instant uploadedAt) {
    }

    record AttachmentContent(String fileName, String contentType, byte[] content) {
    }

    /** {@code categoryId} همیشه غیر-null است (categoryId روی ExpenseEntity اجباری است). */
    record CategorySummary(Long categoryId, String categoryName, BigDecimal total, long count) {
    }

    /** {@code vendorId}/{@code vendorName} می‌توانند null باشند (هزینه‌ی بدون طرف حساب). */
    record VendorSummary(Long vendorId, String vendorName, BigDecimal total, long count) {
    }

    record MethodSummary(PaymentMethod method, BigDecimal total, long count) {
    }

    record FinanceSummary(BigDecimal income, BigDecimal expense) {
    }
}
