package com.example.roshdeandishe.academic.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * یک درس در فهرست سراسری درس‌ها (openspec/changes/grades-by-course.md):
 * مشترک بین همه‌ی مدرسه‌ها (بدون {@code schoolId}) و به‌تفکیک پایه. در این
 * فاز فقط خواندنی است - برنامه هیچ مسیر نوشتنی ندارد و ردیف‌ها را اپراتور
 * پلتفرم با مهاجرت داده مدیریت می‌کند؛ پس عمداً setter و سازنده‌ی عمومی
 * ندارد. نمره‌ی یک درس با {@code GradeEntity.courseId} به آن اشاره می‌کند.
 */
@Entity
@Table(name = "courses", schema = "academic")
@Getter
@NoArgsConstructor
class CourseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "grade_level", nullable = false)
    private int gradeLevel;

    @Column(nullable = false, length = 128)
    private String name;

    /**
     * {@code false} = درسی که در برنامهٔ کلاسی می‌آید ولی در کارنامه نمی‌آید:
     * نمره‌دادن به آن رد می‌شود و انتخابگر درسِ دفتر نمرات آن را نشان نمی‌دهد
     * (openspec/changes/class-timetable.md). پیش‌فرض true.
     */
    @Column(nullable = false)
    private boolean graded = true;

    /**
     * بازه‌ی اعتبار به «سالِ شروعِ» سال تحصیلی (1404 برای 1404-1405)، هر دو سر شامل؛ خالی = بدون محدودیت
     * (openspec/changes/academic-year.md فاز ۱). مقدارها را مهاجرتِ داده می‌گذارد، نه برنامه.
     */
    @Column(name = "valid_from_year")
    private Integer validFromYear;

    @Column(name = "valid_until_year")
    private Integer validUntilYear;

    /** آیا درس برای سالِ تحصیلیِ با این سالِ شروع ارائه می‌شود؟ */
    boolean isOfferedIn(int startYear) {
        return (validFromYear == null || validFromYear <= startYear)
                && (validUntilYear == null || startYear <= validUntilYear);
    }
}
