package com.example.roshdeandishe.school.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

/**
 * بازه‌ی گسترش‌یافته‌ی برنامه‌ی سالانه‌ی یک مدرسه در یک سال تحصیلی (openspec/changes/yearly-plan.md).
 * نبودنِ ردیف یعنی بازه‌ی پیش‌فرضِ سال؛ این ردیف فقط وقتی هست که بازه از پیش‌فرض بزرگ‌تر باشد.
 * {@code createdAt} را دیتابیس پر می‌کند.
 */
@Entity
@Table(name = "school_year_ranges", schema = "school")
@IdClass(SchoolYearRangeEntity.Key.class)
@Getter
@NoArgsConstructor
class SchoolYearRangeEntity {

    @Id
    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Id
    @Column(name = "academic_year_id", nullable = false)
    private Long academicYearId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    SchoolYearRangeEntity(Long schoolId, Long academicYearId, LocalDate startDate, LocalDate endDate) {
        this.schoolId = schoolId;
        this.academicYearId = academicYearId;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    void setRange(LocalDate startDate, LocalDate endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    static class Key implements Serializable {
        private Long schoolId;
        private Long academicYearId;
    }
}
