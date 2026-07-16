package com.example.roshdeandishe.studyplan.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * یک بلوک مطالعه‌ی زمان‌بندی‌شده برای یک دانش‌آموز. {@code startTime}/
 * {@code endTime} ساعت دیواری برنامه‌اند ({@code LocalDateTime}، همان
 * قرارداد LocalDate در expenseDate/dueDate برای مقادیر تقویمی دامنه) -
 * نه timestamp رویداد که Instant می‌بود. {@code topicId} مات به curriculum؛
 * زیرسرفصل‌ها در جدول فرزند study_interval_sub_topics.
 */
@Entity
@Table(name = "study_intervals", schema = "studyplan")
@Getter
@Setter
@NoArgsConstructor
class StudyIntervalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(nullable = false)
    private boolean done;

    @Column(name = "test_count", nullable = false)
    private int testCount;

    StudyIntervalEntity(Long schoolId, Long studentId, Long topicId,
                        LocalDateTime startTime, LocalDateTime endTime) {
        this.schoolId = schoolId;
        this.studentId = studentId;
        this.topicId = topicId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.done = false;
        this.testCount = 0;
    }
}
