package com.example.roshdeandishe.finance.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * بایت‌های واقعی یک پیوست - جدا از متادیتا (رجوع به doc
 * {@link ExpenseAttachmentEntity}).
 * <p>
 * عمداً {@code byte[]} ساده و بدون {@code @Lob}: در Hibernate 6+ روی
 * PostgreSQL این به {@code bytea} نگاشت می‌شود (درست)؛ {@code @Lob} به
 * large object از نوع {@code oid} می‌رسید که dump/restore را می‌شکند و
 * پاک‌سازی lo جداگانه می‌خواهد. {@code @JdbcTypeCode} فقط برای مستندسازی
 * صریح همین تصمیم است.
 */
@Entity
@Table(name = "expense_attachment_contents", schema = "finance")
@Getter
@Setter
@NoArgsConstructor
class ExpenseAttachmentContentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attachment_id", nullable = false, unique = true)
    private Long attachmentId;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false)
    private byte[] content;

    ExpenseAttachmentContentEntity(Long attachmentId, byte[] content) {
        this.attachmentId = attachmentId;
        this.content = content;
    }
}
