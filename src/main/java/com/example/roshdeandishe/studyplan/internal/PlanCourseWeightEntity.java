package com.example.roshdeandishe.studyplan.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * وزن یک درس در یک برنامه - پورت آیتم‌های آرایه‌ی course_info موshavere
 * ({course, weight, priority}) به‌صورت جدول فرزند. {@code topicId} یک Long
 * مات به سرفصل curriculum است (اعتبارسنجی facade-سطح، همان قرارداد
 * teacherId/studentId جاهای دیگر).
 */
@Entity
@Table(name = "plan_course_weights", schema = "studyplan",
        uniqueConstraints = @UniqueConstraint(columnNames = {"plan_id", "topic_id"}))
@Getter
@Setter
@NoArgsConstructor
class PlanCourseWeightEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Column(nullable = false)
    private double weight;

    @Column(nullable = false)
    private int priority;

    PlanCourseWeightEntity(Long planId, Long topicId, double weight, int priority) {
        this.planId = planId;
        this.topicId = topicId;
        this.weight = weight;
        this.priority = priority;
    }
}
