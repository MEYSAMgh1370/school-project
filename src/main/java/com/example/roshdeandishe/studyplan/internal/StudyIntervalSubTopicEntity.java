package com.example.roshdeandishe.studyplan.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * زیرسرفصل‌های یک بازه‌ی مطالعه - پورت ManyToMany sub_topics موshavere.
 */
@Entity
@Table(name = "study_interval_sub_topics", schema = "studyplan",
        uniqueConstraints = @UniqueConstraint(columnNames = {"interval_id", "topic_id"}))
@Getter
@Setter
@NoArgsConstructor
class StudyIntervalSubTopicEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "interval_id", nullable = false)
    private Long intervalId;

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    StudyIntervalSubTopicEntity(Long intervalId, Long topicId) {
        this.intervalId = intervalId;
        this.topicId = topicId;
    }
}
