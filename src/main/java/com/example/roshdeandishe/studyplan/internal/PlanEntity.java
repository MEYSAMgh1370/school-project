package com.example.roshdeandishe.studyplan.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * برنامه‌ی درسی یک مدرسه. {@code teacherId} یک Long مات به iam User است
 * (بدون FK بین‌ماژولی، همان قرارداد ClassEntity.teacherId) و nullable -
 * سازنده از کاربر جاری برداشته می‌شود و مسیرهای بدون کاربر (تست/seed)
 * نباید بشکنند. وزن‌دهی درس‌ها در جدول فرزند plan_course_weights است،
 * نه ستون JSON (تصمیم پاس new-module - رجوع به StudyPlanFacade).
 */
@Entity
@Table(name = "plans", schema = "studyplan")
@Getter
@Setter
@NoArgsConstructor
class PlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "teacher_id")
    private Long teacherId;

    @Column(nullable = false, length = 256)
    private String title;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    PlanEntity(Long schoolId, Long teacherId, String title, Instant createdAt) {
        this.schoolId = schoolId;
        this.teacherId = teacherId;
        this.title = title;
        this.createdAt = createdAt;
    }
}
