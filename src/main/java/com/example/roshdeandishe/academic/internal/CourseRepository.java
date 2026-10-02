package com.example.roshdeandishe.academic.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface CourseRepository extends JpaRepository<CourseEntity, Long> {

    /** همه‌ی درس‌های پایه، شاملِ غیرنمره‌پذیر - انتخابگرِ درسِ برنامهٔ کلاسی. */
    List<CourseEntity> findByGradeLevelOrderByNameAsc(int gradeLevel);

    /** فقط درس‌هایی که در کارنامه می‌آیند - انتخابگرِ درسِ دفتر نمرات. */
    List<CourseEntity> findByGradeLevelAndGradedTrueOrderByNameAsc(int gradeLevel);
}
