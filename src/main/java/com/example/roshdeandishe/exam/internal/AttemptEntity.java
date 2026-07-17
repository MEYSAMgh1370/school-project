package com.example.roshdeandishe.exam.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * تلاش یک دانش‌آموز برای شرکت در یک آزمون - پورت {@code Attempt} موshavere.
 * {@code unique(examId, studentId)}: «فقط آزمون تک‌تلاشی پشتیبانی می‌شود»
 * (کامنت صریح منبع) - همان {@code unique_together} آنجا.
 * <p>
 * {@code expireAt} (نه یک {@code @Transient} property مثل منبع) در لحظه‌ی
 * ساخت محاسبه و ذخیره می‌شود: {@code min(startedAt + exam.durationMinutes,
 * exam.endAt)} - چون توکن exam-take خودش ذخیره نمی‌شود (هر بار از روی
 * همین دو فیلد دوباره صادر می‌شود، رجوع به ExamFacadeImpl.startAttempt)،
 * این مقدار باید در دیتابیس بماند، نه فقط در حافظه.
 */
@Entity
@Table(name = "attempts", schema = "exam",
        uniqueConstraints = @UniqueConstraint(columnNames = {"exam_id", "student_id"}))
@Getter
@Setter
@NoArgsConstructor
class AttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exam_id", nullable = false)
    private Long examId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "expire_at", nullable = false)
    private Instant expireAt;

    AttemptEntity(Long examId, Long studentId, Instant startedAt, Instant expireAt) {
        this.examId = examId;
        this.studentId = studentId;
        this.startedAt = startedAt;
        this.expireAt = expireAt;
    }
}
