package com.example.roshdeandishe.curriculum.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * یک سوال چهارگزینه‌ای در بانک سوال یک مدرسه، همیشه زیر یک سرفصل
 * ({@code topicId} اجباری - بر خلاف moshavere که topic را nullable گذاشته
 * بود؛ سوال بدون سرفصل در این مدل جایی در فیلتر/آزمون‌سازی ندارد).
 * <p>
 * {@code difficulty} (عدد صحیح نامنفی) و {@code discriminationIndex}
 * (شاخص تمیز - float در moshavere) هر دو عمداً nullable اند: بانک سوال
 * قبل از هر تحلیل آماری هم قابل استفاده است.
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

    @Column(nullable = false, length = 4000)
    private String text;

    @Column(name = "choice1", nullable = false, length = 2000)
    private String choice1;

    @Column(name = "choice2", nullable = false, length = 2000)
    private String choice2;

    @Column(name = "choice3", nullable = false, length = 2000)
    private String choice3;

    @Column(name = "choice4", nullable = false, length = 2000)
    private String choice4;

    @Column(name = "correct_choice", nullable = false)
    private int correctChoice;

    private Integer difficulty;

    @Column(name = "discrimination_index")
    private Double discriminationIndex;

    QuestionEntity(Long schoolId, Long topicId, String text,
                   String choice1, String choice2, String choice3, String choice4,
                   int correctChoice, Integer difficulty, Double discriminationIndex) {
        this.schoolId = schoolId;
        this.topicId = topicId;
        this.text = text;
        this.choice1 = choice1;
        this.choice2 = choice2;
        this.choice3 = choice3;
        this.choice4 = choice4;
        this.correctChoice = correctChoice;
        this.difficulty = difficulty;
        this.discriminationIndex = discriminationIndex;
    }
}
