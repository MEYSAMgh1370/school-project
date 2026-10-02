package com.example.roshdeandishe.academic.internal;

import com.example.roshdeandishe.academic.GradingTerm;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
