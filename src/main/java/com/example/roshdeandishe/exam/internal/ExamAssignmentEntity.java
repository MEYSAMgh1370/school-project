package com.example.roshdeandishe.exam.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * دانش‌آموزی که برای شرکت در یک آزمون COLLECTIVE تعیین شده - پورت
 * {@code CollectiveExam.students} (ManyToMany جنگو) به یک جدول join
 * صریح، مطابق ترجیح این codebase به join table به‌جای رابطه‌ی ضمنی
 * چارچوب (همان الگوی RolePermissionEntity/WeekCourseTopicEntity). فقط
 * برای آزمون‌های COLLECTIVE معنا دارد؛ WEEKLY مالکیتش را مستقیم از
 * {@code ExamEntity.studentId} می‌گیرد.
 */
@Entity
@Table(name = "exam_assignments", schema = "exam",
        uniqueConstraints = @UniqueConstraint(columnNames = {"exam_id", "student_id"}))
@Getter
@Setter
@NoArgsConstructor
class ExamAssignmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exam_id", nullable = false)
    private Long examId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    ExamAssignmentEntity(Long examId, Long studentId) {
        this.examId = examId;
        this.studentId = studentId;
    }
}
