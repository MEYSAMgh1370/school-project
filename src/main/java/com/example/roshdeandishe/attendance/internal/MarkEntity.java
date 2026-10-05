package com.example.roshdeandishe.attendance.internal;

import com.example.roshdeandishe.attendance.AttendanceStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * یک استثنا: دانش‌آموز در این کلاس و روز غایب یا دیرآمده بود. فقط استثناها ذخیره می‌شوند؛ دانش‌آموزِ یک
 * {@link RollEntity} بدونِ مُهر حاضر است. {@code excused} فقط برای ABSENT معنا دارد.
 */
@Entity
@Table(name = "marks", schema = "attendance")
@Getter
@Setter
@NoArgsConstructor
class MarkEntity {

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private AttendanceStatus status;

    @Column(nullable = false)
    private boolean excused;

    @Column(length = 255)
    private String note;

    @Column(name = "marked_by_user_id")
    private Long markedByUserId;

    @Column(name = "marked_at", nullable = false)
    private Instant markedAt;

    MarkEntity(Long schoolId, Long classId, LocalDate day, Long studentId, AttendanceStatus status, boolean excused,
               String note, Long markedByUserId, Instant markedAt) {
        this.schoolId = schoolId;
        this.classId = classId;
        this.day = day;
        this.studentId = studentId;
        this.status = status;
        this.excused = excused;
        this.note = note;
        this.markedByUserId = markedByUserId;
        this.markedAt = markedAt;
    }
}
