package com.example.roshdeandishe.studyplan.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * پورت {@code StudyInterval.sub_topics} (ManyToMany در منبع) به یک جدول
 * join صریح - همان دلیل WeekCourseTopicEntity.
 */
@Entity
@Table(name = "study_interval_sub_topics", schema = "studyplan")
@Getter
@Setter
@NoArgsConstructor
class StudyIntervalSubTopicEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "study_interval_id", nullable = false)
    private Long studyIntervalId;

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    StudyIntervalSubTopicEntity(Long studyIntervalId, Long topicId) {
        this.studyIntervalId = studyIntervalId;
        this.topicId = topicId;
    }
}
