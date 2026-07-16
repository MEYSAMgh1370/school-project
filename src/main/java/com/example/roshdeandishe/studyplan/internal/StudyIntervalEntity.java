package com.example.roshdeandishe.studyplan.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * یک بازه‌ی زمانیِ مطالعه برای یک دانش‌آموز - پورت مدل StudyInterval از
 * study_table/models.py. {@code studentId} همان قرارداد مبهم بدون FK
 * (رجوع به StudentPlanAssignmentEntity بالا). {@code done}/{@code testCount}
 * معادل is_done/test_count منبع‌اند.
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

    StudyIntervalEntity(Long schoolId, Long studentId, Long topicId, LocalDateTime startTime, LocalDateTime endTime) {
        this.schoolId = schoolId;
        this.studentId = studentId;
        this.topicId = topicId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.done = false;
        this.testCount = 0;
    }
}
