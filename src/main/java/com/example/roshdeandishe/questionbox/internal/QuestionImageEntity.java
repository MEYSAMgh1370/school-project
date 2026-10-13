package com.example.roshdeandishe.questionbox.internal;

import com.example.roshdeandishe.questionbox.QuestionBoxFacade.ImageRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * تصویرِ یک سؤال، با بایت‌ها در ستون bytea. عمداً {@code byte[]} ساده بدون {@code @Lob} (استدلالِ
 * {@code finance.internal.ExpenseAttachmentContentEntity}: {@code @Lob} به large object از نوع oid می‌رسید).
 */
@Entity
@Table(name = "question_images", schema = "questionbox")
@Getter
@NoArgsConstructor
class QuestionImageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ImageRole role;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false)
    private byte[] data;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    QuestionImageEntity(Long questionId, ImageRole role, String contentType, byte[] data, Instant createdAt) {
        this.questionId = questionId;
        this.role = role;
        this.contentType = contentType;
        this.data = data;
        this.createdAt = createdAt;
    }
}
