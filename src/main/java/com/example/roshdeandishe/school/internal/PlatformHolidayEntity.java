package com.example.roshdeandishe.school.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * تعطیلیِ سراسریِ پلتفرم برای یک سال تحصیلی (openspec/changes/yearly-plan.md، فاز B):
 * بدون {@code schoolId}، چون در همه‌ی مدرسه‌ها اثر دارد. {@code day} تاریخ میلادیِ ISO
 * است و {@code createdAt} را دیتابیس پر می‌کند.
 */
@Entity
@Table(name = "platform_holidays", schema = "school")
@Getter
@NoArgsConstructor
class PlatformHolidayEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year_id", nullable = false)
    private Long academicYearId;

    @Column(nullable = false)
    private LocalDate day;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    PlatformHolidayEntity(Long academicYearId, LocalDate day, String title) {
        this.academicYearId = academicYearId;
        this.day = day;
        this.title = title;
    }
}
