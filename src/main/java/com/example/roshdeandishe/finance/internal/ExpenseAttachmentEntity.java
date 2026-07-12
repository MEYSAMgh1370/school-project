package com.example.roshdeandishe.finance.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * متادیتای پیوست هزینه (اسکن فاکتور، رسید بانکی...). بایت‌های فایل عمداً
 * در جدول جدا ({@link ExpenseAttachmentContentEntity}) هستند تا فهرست کردن
 * پیوست‌ها هرگز blob بارگذاری نکند - lazy روی {@code @Basic} به bytecode
 * enhancement نیاز دارد که این پروژه ندارد؛ جدول جدا مسیر امن را ساختاری
 * می‌کند نه وابسته به انضباط کوئری.
 */
@Entity
@Table(name = "expense_attachments", schema = "finance")
@Getter
@Setter
@NoArgsConstructor
class ExpenseAttachmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "expense_id", nullable = false)
    private Long expenseId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "uploaded_by_user_id")
    private Long uploadedByUserId;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    ExpenseAttachmentEntity(Long schoolId, Long expenseId, String fileName, String contentType,
                            long sizeBytes, Long uploadedByUserId, Instant uploadedAt) {
        this.schoolId = schoolId;
        this.expenseId = expenseId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.uploadedByUserId = uploadedByUserId;
        this.uploadedAt = uploadedAt;
    }
}
