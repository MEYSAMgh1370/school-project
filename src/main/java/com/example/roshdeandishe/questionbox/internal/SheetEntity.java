package com.example.roshdeandishe.questionbox.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * برگه‌ی چاپیِ ذخیره‌شده. {@code headerValues} مقدارهای فیلدهای سربرگ (تاریخ، مدت، ...) به‌صورت JSON است.
 */
@Entity
@Table(name = "sheets", schema = "questionbox")
@Getter
@Setter
@NoArgsConstructor
class SheetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(nullable = false)
    private String title;

    @Column(name = "header_id")
    private Long headerId;

    @Column(name = "header_values", nullable = false, columnDefinition = "text")
    private String headerValues = "{}";

    @Column(name = "show_essay_answer_space", nullable = false)
    private boolean showEssayAnswerSpace;

    @Column(name = "include_answer_key", nullable = false)
    private boolean includeAnswerKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    SheetEntity(Long schoolId, Long createdByUserId, Instant now) {
        this.schoolId = schoolId;
        this.createdByUserId = createdByUserId;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
