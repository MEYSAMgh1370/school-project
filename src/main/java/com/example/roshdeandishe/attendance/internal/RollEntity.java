package com.example.roshdeandishe.attendance.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * «حضور و غیابِ این کلاس در این روز گرفته شد» (attendance.md). بدونِ این ردیف کلاس «ثبت نشده» است، که با
 * «هیچ‌کس غایب نبود» یکی نیست. کلاس و کاربر شناسه‌ی مات‌اند، بدونِ کلیدِ خارجیِ بین‌ماژولی.
 */
@Entity
@Table(name = "rolls", schema = "attendance")
@Getter
@Setter
@NoArgsConstructor
class RollEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(nullable = false)
    private LocalDate day;

    @Column(name = "taken_by_user_id")
    private Long takenByUserId;

    @Column(name = "taken_at", nullable = false)
    private Instant takenAt;

    RollEntity(Long schoolId, Long classId, LocalDate day, Long takenByUserId, Instant takenAt) {
        this.schoolId = schoolId;
        this.classId = classId;
        this.day = day;
        this.takenByUserId = takenByUserId;
        this.takenAt = takenAt;
    }
}
