package com.example.roshdeandishe.curriculum.internal;

import com.example.roshdeandishe.curriculum.QuestionDifficulty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * یک سؤال چهارگزینه‌ای از بانک سؤال مدرسه - پورت مدل Question از
 * exam_generator/models.py در moshavere. متن سؤال/گزینه‌ها/پاسخ تشریحی
 * ستون text هستند (در منبع TextField بی‌کران بود؛ صورت سؤال ریاضی
 * می‌تواند بلند باشد) - این پروژه از قبل به Postgres قفل است (رجوع به
 * استدلال bytea در ExpenseAttachmentContentEntity).
 * <p>
 * {@code topicId} برخلاف منبع (nullable) اجباری است: فیلترِ سرفصل در
 * moshavere هم عملاً required بود و سؤالِ بدون سرفصل از هیچ مسیر خواندنی
 * پیدا نمی‌شد - یک wart، نه الگویی که ارزش حفظ داشته باشد.
 * {@code difficulty} و {@code discriminationIndex} مثل منبع nullable اند
 * (سؤال هنوز ارزیابی‌نشده).
 */
@Entity
@Table(name = "questions", schema = "curriculum")
@Getter
@Setter
@NoArgsConstructor
class QuestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(nullable = false, columnDefinition = "text")
    private String choice1;

    @Column(nullable = false, columnDefinition = "text")
    private String choice2;

    @Column(nullable = false, columnDefinition = "text")
    private String choice3;

    @Column(nullable = false, columnDefinition = "text")
    private String choice4;

    @Column(name = "correct_choice", nullable = false)
    private int correctChoice;

    @Column(columnDefinition = "text")
    private String answer;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private QuestionDifficulty difficulty;

    @Column(name = "discrimination_index")
    private Double discriminationIndex;

    QuestionEntity(
            Long schoolId,
            Long topicId,
            String text,
            String choice1,
            String choice2,
            String choice3,
            String choice4,
            int correctChoice,
            String answer,
            QuestionDifficulty difficulty,
            Double discriminationIndex
    ) {
        this.schoolId = schoolId;
        this.topicId = topicId;
        this.text = text;
        this.choice1 = choice1;
        this.choice2 = choice2;
        this.choice3 = choice3;
        this.choice4 = choice4;
        this.correctChoice = correctChoice;
        this.answer = answer;
        this.difficulty = difficulty;
        this.discriminationIndex = discriminationIndex;
    }
}
