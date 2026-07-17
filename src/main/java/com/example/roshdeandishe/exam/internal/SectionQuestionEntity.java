package com.example.roshdeandishe.exam.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * یک سؤال مشخص (مات به ماژول curriculum) که برای یک بخش انتخاب شده - چه
 * با انتخاب دستی مدرس، چه تصادفی از زیردرخت سرفصلِ بخش. پورت
 * {@code SectionQuestion} موshavere؛ {@link SubmitEntity} به همین ردیف
 * (نه مستقیم به questionId) اشاره می‌کند - همان قرارداد منبع.
 */
@Entity
@Table(name = "section_questions", schema = "exam",
        uniqueConstraints = @UniqueConstraint(columnNames = {"section_id", "question_id"}))
@Getter
@Setter
@NoArgsConstructor
class SectionQuestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    SectionQuestionEntity(Long sectionId, Long questionId) {
        this.sectionId = sectionId;
        this.questionId = questionId;
    }
}
