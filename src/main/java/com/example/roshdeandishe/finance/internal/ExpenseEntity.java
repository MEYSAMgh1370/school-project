package com.example.roshdeandishe.finance.internal;

import com.example.roshdeandishe.finance.ExpenseStatus;
import com.example.roshdeandishe.finance.PaymentMethod;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * یک هزینه‌ی مدرسه (حقوق، قبض، تجهیزات...). categoryId اجباری (بدون
 * دسته‌بندی هزینه‌ی گزارش‌پذیری وجود ندارد)، vendorId اختیاری - هر دو
 * Long خام با اعتبارسنجیِ وجود/تعلق‌به‌مدرسه در ExpenseFacadeImpl، همان
 * قرارداد بدون-JPA-relation بقیه‌ی این codebase (teacherId/studentId).
 * <p>
 * expenseDate از نوع LocalDate (نه Instant): تاریخِ «انجام هزینه» یک روزِ
 * تقویمی است که ادمین انتخاب می‌کند، نه یک لحظه‌ی ثبت - createdAt/updatedAt
 * لحظه‌های ثبت‌اند و Instant می‌مانند.
 * <p>
 * status واقعاً persist می‌شود (برخلاف InvoiceStatus مشتق‌شده) - رجوع به
 * doc خود ExpenseStatus. حذف فیزیکی وجود ندارد؛ CANCELLED جایگزین است.
 */
@Entity
@Table(name = "expenses", schema = "finance")
@Getter
@Setter
@NoArgsConstructor
class ExpenseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "vendor_id")
    private Long vendorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 32)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExpenseStatus status;

    @Column(length = 512)
    private String description;

    @Column(name = "receipt_number", length = 64)
    private String receiptNumber;

    @Column(name = "invoice_number", length = 64)
    private String invoiceNumber;

    /** شماره سند داخلی مدرسه - برای اتصال آینده به نرم‌افزار حسابداری. */
    @Column(name = "document_number", length = 64)
    private String documentNumber;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    ExpenseEntity(Long schoolId, LocalDate expenseDate, BigDecimal amount, Long categoryId,
                  Long vendorId, PaymentMethod paymentMethod, String description,
                  String receiptNumber, String invoiceNumber, String documentNumber,
                  Long createdByUserId, Instant createdAt) {
        this.schoolId = schoolId;
        this.expenseDate = expenseDate;
        this.amount = amount;
        this.categoryId = categoryId;
        this.vendorId = vendorId;
        this.paymentMethod = paymentMethod;
        this.status = ExpenseStatus.DRAFT;
        this.description = description;
        this.receiptNumber = receiptNumber;
        this.invoiceNumber = invoiceNumber;
        this.documentNumber = documentNumber;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }
}
