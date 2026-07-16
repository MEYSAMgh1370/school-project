package com.example.roshdeandishe.studyplan.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * پورت {@code WeekCourse.topics} (ArrayField تا ۱۰ شناسه‌ی زیر-سرفصل) به
 * یک جدول join واقعی - مطابق ترجیح این codebase برای جدول join به‌جای
 * ستون آرایه (مثل RolePermissionEntity)، نه معرفی یک الگوی ستون آرایه‌ی
 * تازه (MOSHAVERE_PORT_SPEC.md §6).
 */
@Entity
@Table(name = "week_course_topics", schema = "studyplan")
@Getter
@Setter
@NoArgsConstructor
class WeekCourseTopicEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "week_course_id", nullable = false)
    private Long weekCourseId;

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    WeekCourseTopicEntity(Long weekCourseId, Long topicId, int sortOrder) {
        this.weekCourseId = weekCourseId;
        this.topicId = topicId;
        this.sortOrder = sortOrder;
    }
}
