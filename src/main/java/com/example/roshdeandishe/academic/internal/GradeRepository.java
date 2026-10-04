package com.example.roshdeandishe.academic.internal;

import com.example.roshdeandishe.academic.GradingTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

interface GradeRepository extends JpaRepository<GradeEntity, Long> {

    Optional<GradeEntity> findByStudentIdAndClassIdAndAcademicYearAndTermAndCourseId(
            Long studentId, Long classId, String academicYear, GradingTerm term, Long courseId);

    Optional<GradeEntity> findByStudentIdAndClassIdAndAcademicYearAndTermAndCourseIdIsNull(
            Long studentId, Long classId, String academicYear, GradingTerm term);

    List<GradeEntity> findByClassIdAndAcademicYearAndTermAndCourseId(
            Long classId, String academicYear, GradingTerm term, Long courseId);

    List<GradeEntity> findByClassIdAndAcademicYearAndTermAndCourseIdIsNull(
            Long classId, String academicYear, GradingTerm term);

    List<GradeEntity> findByStudentIdIn(List<Long> studentIds);

    /** شمارِ نمره‌ها به‌تفکیکِ (مدرسه، سال) - برای گزارشِ سال‌های خارج از فهرست. ردیف: [schoolId, academicYear, count]. */
    @Query("select g.schoolId, g.academicYear, count(g) from GradeEntity g group by g.schoolId, g.academicYear")
    List<Object[]> countBySchoolAndYear();
}
