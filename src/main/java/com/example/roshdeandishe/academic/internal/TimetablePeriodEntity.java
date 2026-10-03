package com.example.roshdeandishe.academic.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/** یک زنگ: مدرسه آن را برای یک سال تحصیلی (کدِ فهرستِ سراسری) تعریف می‌کند. */
@Entity
@Table(name = "timetable_periods", schema = "academic")
@Getter
@Setter
@NoArgsConstructor
class TimetablePeriodEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "academic_year", nullable = false, length = 16)
    private String academicYear;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 64)
    private String label;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    TimetablePeriodEntity(Long schoolId, String academicYear, int position, String label,
                          LocalTime startTime, LocalTime endTime) {
        this.schoolId = schoolId;
        this.academicYear = academicYear;
        this.position = position;
        this.label = label;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
