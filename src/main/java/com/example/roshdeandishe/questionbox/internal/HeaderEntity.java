package com.example.roshdeandishe.questionbox.internal;

import com.example.roshdeandishe.questionbox.QuestionBoxFacade.HeaderLayout;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade.HeaderMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * سربرگِ قابل‌استفاده‌ی مجدد: قالب آماده (LAYOUT + فیلدها) یا تصویر (IMAGE). {@code fields} فهرستِ کدهای
 * HeaderField با ویرگول است. بایت‌های تصویر بدون {@code @Lob}، مثل QuestionImageEntity.
 */
@Entity
@Table(name = "headers", schema = "questionbox")
@Getter
@Setter
@NoArgsConstructor
class HeaderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private HeaderMode mode;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private HeaderLayout layout;

    @Column(name = "show_school_name", nullable = false)
    private boolean showSchoolName;

    @Column(name = "show_logo", nullable = false)
    private boolean showLogo;

    @Column(nullable = false)
    private String fields = "";

    @Column(name = "image_content_type", length = 64)
    private String imageContentType;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column
    private byte[] image;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    HeaderEntity(Long schoolId, Long createdByUserId, Instant now) {
        this.schoolId = schoolId;
        this.createdByUserId = createdByUserId;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
