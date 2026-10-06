package com.example.roshdeandishe.school.internal;

import com.example.roshdeandishe.school.SchoolFacade.CalendarKind;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * یک روزِ خاص در تقویم مدرسه (openspec/changes/attendance.md). {@code day} تاریخ
 * میلادیِ ISO است؛ {@code createdAt} را دیتابیس پر می‌کند.
 */
@Entity
@Table(name = "calendar_entries", schema = "school")
@Getter
@NoArgsConstructor
class CalendarEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(nullable = false)
    private LocalDate day;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CalendarKind kind;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    CalendarEntryEntity(Long schoolId, LocalDate day, CalendarKind kind, String title) {
        this.schoolId = schoolId;
        this.day = day;
        this.kind = kind;
        this.title = title;
    }
}
