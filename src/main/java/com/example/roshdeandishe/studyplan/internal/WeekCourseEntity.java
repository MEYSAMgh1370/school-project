package com.example.roshdeandishe.studyplan.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * پورت مدل WeekCourse از plan/models.py: کدام سرفصل ("course") در کدام
 * هفته برای یک Plan برنامه‌ریزی شده. منبع {@code date__week_day=7} را با
 * CheckConstraint دیتابیسی اجرا می‌کرد (شنبه = ۷ در قرارداد هفته‌ی
 * Django)؛ اینجا عمداً به‌جای آن در سطح Facade اعتبارسنجی می‌شود
 * (StudyPlanError.InvalidWeekCourse) - همان قراردادِ این codebase برای
 * قوانین دامنه (مثل مبلغ منفی در FinanceFacade)، نه CHECK constraint.
 * <p>
 * {@code schoolId} این‌جا هم denormalized نگه داشته شده (با اینکه از
 * طریق planId قابل استخراج است) تا اعتبارسنجی چندمستأجری بدون join
 * مستقیم روی همین جدول قابل انجام باشد - همان الگوی ClassEntity/StudentEntity.
 */
@Entity
@Table(name = "week_courses", schema = "studyplan")
@Getter
@Setter
@NoArgsConstructor
class WeekCourseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    WeekCourseEntity(Long schoolId, Long planId, LocalDate weekStartDate, Long topicId) {
        this.schoolId = schoolId;
        this.planId = planId;
        this.weekStartDate = weekStartDate;
        this.topicId = topicId;
    }
}
