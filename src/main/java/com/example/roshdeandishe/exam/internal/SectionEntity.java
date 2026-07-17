package com.example.roshdeandishe.exam.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * یک بخش از آزمون، متعلق به یک سرفصل مشخص ({@code topicId}، مات به ماژول
 * curriculum) - پورت {@code Section} موshavere. سؤال‌های واقعیِ این بخش در
 * {@link SectionQuestionEntity} نگهداری می‌شوند (چه با انتخاب دستی، چه
 * تصادفی از زیردرخت همین سرفصل). {@code unique(examId, topicId)} همان
 * {@code unique_together} منبع است.
 */
@Entity
@Table(name = "sections", schema = "exam",
        uniqueConstraints = @UniqueConstraint(columnNames = {"exam_id", "topic_id"}))
@Getter
@Setter
@NoArgsConstructor
class SectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exam_id", nullable = false)
    private Long examId;

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Column(length = 200)
    private String name;

    SectionEntity(Long examId, Long topicId, String name) {
        this.examId = examId;
        this.topicId = topicId;
        this.name = name;
    }
}
