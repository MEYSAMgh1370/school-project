package com.example.roshdeandishe.attendance.internal;

import com.example.roshdeandishe.attendance.ExcuseStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * عذرِ ولی برای یک غیبت (attendance.md «Excuses»). برای هر (دانش‌آموز، روز) یک ردیف. کلاس و کاربر شناسه‌ی
 * مات‌اند، بدونِ کلیدِ خارجیِ بین‌ماژولی. جدول با V13 ساخته شده است.
 */
@Entity
@Table(name = "excuses", schema = "attendance")
@Getter
@Setter
@NoArgsConstructor
class ExcuseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(nullable = false)
    private LocalDate day;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(nullable = false, length = 500)
    private String note;

    @Column(name = "submitted_by_user_id", nullable = false)
    private Long submittedByUserId;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ExcuseStatus status;

    @Column(name = "decided_by_user_id")
    private Long decidedByUserId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    ExcuseEntity(Long schoolId, Long classId, LocalDate day, Long studentId, String note, Long submittedByUserId,
                 Instant submittedAt) {
        this.schoolId = schoolId;
        this.classId = classId;
        this.day = day;
        this.studentId = studentId;
        this.note = note;
        this.submittedByUserId = submittedByUserId;
        this.submittedAt = submittedAt;
        this.status = ExcuseStatus.PENDING;
    }
}
