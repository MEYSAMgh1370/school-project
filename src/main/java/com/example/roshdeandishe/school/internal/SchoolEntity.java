package com.example.roshdeandishe.school.internal;

import com.example.roshdeandishe.school.SchoolFacade.SchoolLevel;
import com.example.roshdeandishe.school.SchoolFacade.SchoolStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity داخلی - اولین بار است که schoolId یک ردیف واقعی پشت سرش دارد،
 * نه فقط یک عدد فرضی که هر ماژول دیگری امیدوار است معتبر باشد.
 */
@Entity
@Table(name = "schools", schema = "school")
@Getter
@Setter
@NoArgsConstructor
class SchoolEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SchoolStatus status = SchoolStatus.ACTIVE;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(nullable = false, length = 16)
    private String locale;

    @Enumerated(EnumType.STRING)
    @Column(name = "school_level", nullable = false, length = 32)
    private SchoolLevel schoolLevel;

    /**
     * نشانی اختصاصی مدرسه (اختیاری) - /s/{slug}. nullable چون مدرسه‌های
     * موجود (و هر مدرسه‌ای که نخواهد) نشانی ندارند. طول ۶۳ = سقف DNS label،
     * عمداً، تا در فاز ساب‌دامین همین مقدار بدون migration قابل استفاده باشد.
     */
    @Column(unique = true, length = 63)
    private String slug;

    /**
     * «سال تحصیلی جاریِ» مدرسه (openspec/changes/academic-year.md): مدیرِ همین
     * مدرسه از فهرست سراسریِ {@code AcademicYearEntity} انتخاب می‌کند. null =
     * هنوز انتخاب نشده (مدرسه‌های موجود)؛ هیچ سالی حدس زده نمی‌شود.
     */
    @Column(name = "current_academic_year_id")
    private Long currentAcademicYearId;

    SchoolEntity(String name, String timezone, String locale, SchoolLevel schoolLevel, String slug) {
        this.name = name;
        this.timezone = timezone;
        this.locale = locale;
        this.schoolLevel = schoolLevel;
        this.slug = slug;
        this.status = SchoolStatus.ACTIVE;
    }
}
