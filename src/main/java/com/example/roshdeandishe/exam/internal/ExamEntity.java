package com.example.roshdeandishe.exam.internal;

import com.example.roshdeandishe.exam.ExamType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * یک آزمون - {@code examType} تعیین می‌کند {@code studentId} (WEEKLY، مالک
 * شخصی آزمون) یا {@code teacherId} (COLLECTIVE، مؤلف آزمون) کدام‌یک پر
 * می‌شود؛ دقیقاً همان دوگانگیِ فیلد {@code Exam.author} در منبع moshavere
 * (برای WeeklyExam همان دانش‌آموز، برای CollectiveExam همان مدرس) که اینجا
 * به‌جای یک فیلد چندمعنایی، دو فیلد جدا و nullable شده - همان ترجیح این
 * codebase به وضوح به‌جای وراثت/فیلد چندمعنایی.
 * <p>
 * {@code weekStart} فقط برای WEEKLY پر می‌شود (باید شنبه باشد - همان قید DB
 * موshavere روی WeeklyExam.week).
 */
@Entity
@Table(name = "exams", schema = "exam")
@Getter
@Setter
@NoArgsConstructor
class ExamEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_type", nullable = false, length = 20)
    private ExamType examType;

    @Column(length = 200)
    private String title;

    @Column(name = "teacher_id")
    private Long teacherId;

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "week_start")
    private LocalDate weekStart;

    ExamEntity(Long schoolId, ExamType examType, String title, Long teacherId, Long studentId,
               LocalDateTime startAt, LocalDateTime endAt, int durationMinutes, LocalDate weekStart) {
        this.schoolId = schoolId;
        this.examType = examType;
        this.title = title;
        this.teacherId = teacherId;
        this.studentId = studentId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.durationMinutes = durationMinutes;
        this.weekStart = weekStart;
    }
}
