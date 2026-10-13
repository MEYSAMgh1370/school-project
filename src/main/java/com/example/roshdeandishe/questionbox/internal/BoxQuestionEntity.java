package com.example.roshdeandishe.questionbox.internal;

import com.example.roshdeandishe.questionbox.QuestionBoxFacade.ContentMode;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade.QuestionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * سؤالِ معلم در باکس (چهارگزینه‌ای یا تشریحی، متنی یا فقط‌تصویر). {@code deletedAt} حذفِ نرم است: برگه‌های
 * ذخیره‌شده سؤالِ حذف‌شده را همچنان چاپ می‌کنند. {@code schoolId} و {@code createdByUserId} شناسه‌ی ساده‌اند.
 */
@Entity
@Table(name = "questions", schema = "questionbox")
@Getter
@Setter
@NoArgsConstructor
class BoxQuestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "topic_id")
    private Long topicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private QuestionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_mode", nullable = false, length = 16)
    private ContentMode contentMode;

    @Column(columnDefinition = "text")
    private String text;

    @Column(columnDefinition = "text")
    private String choice1;

    @Column(columnDefinition = "text")
    private String choice2;

    @Column(columnDefinition = "text")
    private String choice3;

    @Column(columnDefinition = "text")
    private String choice4;

    @Column(name = "correct_choice")
    private Integer correctChoice;

    @Column(columnDefinition = "text")
    private String answer;

    @Column(name = "suggested_score")
    private Integer suggestedScore;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    BoxQuestionEntity(Long schoolId, Long createdByUserId, Instant now) {
        this.schoolId = schoolId;
        this.createdByUserId = createdByUserId;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
