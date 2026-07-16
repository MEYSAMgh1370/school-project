package com.example.roshdeandishe.studyplan.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * سرفصلِ (درسِ) یک هفته‌ی مشخص از یک برنامه. {@code weekStart} همیشه شنبه
 * است (قید DB موshavere با اعتبارسنجی facade-سطح پورت شده - همان جایی که
 * بقیه‌ی قواعد این ماژول چک می‌شوند). زیرسرفصل‌ها (تا ۱۰ تا، سقف ArrayField
 * موshavere) در جدول فرزند week_course_topics هستند.
 */
@Entity
@Table(name = "week_courses", schema = "studyplan",
        uniqueConstraints = @UniqueConstraint(columnNames = {"plan_id", "week_start", "course_topic_id"}))
@Getter
@Setter
@NoArgsConstructor
class WeekCourseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "course_topic_id", nullable = false)
    private Long courseTopicId;

    WeekCourseEntity(Long planId, LocalDate weekStart, Long courseTopicId) {
        this.planId = planId;
        this.weekStart = weekStart;
        this.courseTopicId = courseTopicId;
    }
}
