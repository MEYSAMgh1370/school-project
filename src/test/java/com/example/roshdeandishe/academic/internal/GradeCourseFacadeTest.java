package com.example.roshdeandishe.academic.internal;

import com.example.roshdeandishe.academic.AcademicFacade.CourseInfo;
import com.example.roshdeandishe.academic.AcademicFacade.GradeInfo;
import com.example.roshdeandishe.academic.GradingScheme;
import com.example.roshdeandishe.academic.GradingTerm;
import com.example.roshdeandishe.academic.shared.AcademicError;
import com.example.roshdeandishe.academic.shared.AcademicException;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.school.SchoolFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * نمره به تفکیک درس (openspec/changes/grades-by-course.md) را بدون Spring و
 * Docker می‌آزماید: نمرهٔ کل بدون تغییر، درس باید وجود داشته باشد و هم‌پایه‌ی
 * کلاس باشد، و نمرهٔ کل و نمره‌ی هر درس ردیف‌های جدا هستند. یکتاییِ دیتابیس
 * (ایندکس‌های جزئیِ V5) در تست یکپارچه‌ی CI و روی Postgres واقعی پوشش داده
 * می‌شود.
 */
class GradeCourseFacadeTest {

    private static final Long SCHOOL = 5L;
    private static final Long CLASS_ID = 7L;
    private static final Long STUDENT = 100L;
    private static final int GRADE_LEVEL = 5;

    private StudentRepository students;
    private ClassRepository classes;
    private GradeRepository grades;
    private CourseRepository courses;
    private AcademicFacadeImpl facade;

    @BeforeEach
    void setUp() {
        students = mock(StudentRepository.class);
        classes = mock(ClassRepository.class);
        grades = mock(GradeRepository.class);
        courses = mock(CourseRepository.class);
        IamFacade iam = mock(IamFacade.class);
        when(iam.currentUserId()).thenReturn(new UserId(1L));
        facade = new AcademicFacadeImpl(students, classes, mock(StudentGuardianRepository.class), grades, courses,
                iam, mock(SchoolFacade.class), mock(ApplicationEventPublisher.class));

        ClassEntity classEntity = new ClassEntity(SCHOOL, "پنجم الف", 9L, "الف", GRADE_LEVEL, GradingScheme.NUMERIC);
        ReflectionTestUtils.setField(classEntity, "id", CLASS_ID);
        when(classes.findById(CLASS_ID)).thenReturn(Optional.of(classEntity));

        StudentEntity student = new StudentEntity();
        student.setId(STUDENT);
        student.setSchoolId(SCHOOL);
        student.setClassId(CLASS_ID);
        when(students.findById(STUDENT)).thenReturn(Optional.of(student));

        when(grades.save(any(GradeEntity.class))).thenAnswer(invocation -> {
            GradeEntity saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(900L);
            }
            return saved;
        });
    }

    private static CourseEntity course(Long id, int gradeLevel, String name) {
        CourseEntity course = new CourseEntity();
        ReflectionTestUtils.setField(course, "id", id);
        ReflectionTestUtils.setField(course, "gradeLevel", gradeLevel);
        ReflectionTestUtils.setField(course, "name", name);
        return course;
    }

    private GradeEntity savedGrade() {
        ArgumentCaptor<GradeEntity> captor = ArgumentCaptor.forClass(GradeEntity.class);
        verify(grades).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void aGradeWithoutACourseIsTheOverallGradeAndUsesTheOverallLookup() {
        when(grades.findByStudentIdAndClassIdAndAcademicYearAndTermAndCourseIdIsNull(
                STUDENT, CLASS_ID, "1405", GradingTerm.FIRST)).thenReturn(Optional.empty());

        facade.recordGrade(SCHOOL, STUDENT, CLASS_ID, "1405", GradingTerm.FIRST, null, new BigDecimal("17"), null, null);

        assertThat(savedGrade().getCourseId()).isNull();
        verify(courses, never()).findById(any());
        verify(grades, never()).findByStudentIdAndClassIdAndAcademicYearAndTermAndCourseId(
                any(), any(), any(), any(), any());
    }

    @Test
    void aGradeWithACourseOfTheClassGradeLevelIsStoredWithThatCourse() {
        when(courses.findById(30L)).thenReturn(Optional.of(course(30L, GRADE_LEVEL, "ریاضی")));
        when(grades.findByStudentIdAndClassIdAndAcademicYearAndTermAndCourseId(
                STUDENT, CLASS_ID, "1405", GradingTerm.FIRST, 30L)).thenReturn(Optional.empty());

        facade.recordGrade(SCHOOL, STUDENT, CLASS_ID, "1405", GradingTerm.FIRST, null, new BigDecimal("18"), null, 30L);

        assertThat(savedGrade().getCourseId()).isEqualTo(30L);
        verify(grades, never()).findByStudentIdAndClassIdAndAcademicYearAndTermAndCourseIdIsNull(
                any(), any(), any(), any());
    }

    @Test
    void recordingTheSameCourseAgainUpdatesThatRowInPlace() {
        when(courses.findById(30L)).thenReturn(Optional.of(course(30L, GRADE_LEVEL, "ریاضی")));
        GradeEntity existing = new GradeEntity();
        existing.setId(55L);
        existing.setCourseId(30L);
        when(grades.findByStudentIdAndClassIdAndAcademicYearAndTermAndCourseId(
                STUDENT, CLASS_ID, "1405", GradingTerm.FIRST, 30L)).thenReturn(Optional.of(existing));

        facade.recordGrade(SCHOOL, STUDENT, CLASS_ID, "1405", GradingTerm.FIRST, null, new BigDecimal("12"), "بهتر", 30L);

        GradeEntity saved = savedGrade();
        assertThat(saved).isSameAs(existing);
        assertThat(saved.getId()).isEqualTo(55L);
        assertThat(saved.getNumericScore()).isEqualByComparingTo("12");
    }

    @Test
    void anUnknownCourseIsRejectedAndNothingIsStored() {
        when(courses.findById(31L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.recordGrade(
                SCHOOL, STUDENT, CLASS_ID, "1405", GradingTerm.FIRST, null, new BigDecimal("12"), null, 31L))
                .isInstanceOfSatisfying(AcademicException.class, e ->
                        assertThat(e.error()).isEqualTo(new AcademicError.CourseNotFound(31L)));
        verify(grades, never()).save(any());
    }

    @Test
    void aCourseOfAnotherGradeLevelIsRejectedAndNothingIsStored() {
        when(courses.findById(32L)).thenReturn(Optional.of(course(32L, GRADE_LEVEL + 1, "علوم ششم")));

        assertThatThrownBy(() -> facade.recordGrade(
                SCHOOL, STUDENT, CLASS_ID, "1405", GradingTerm.FIRST, null, new BigDecimal("12"), null, 32L))
                .isInstanceOfSatisfying(AcademicException.class, e ->
                        assertThat(e.error()).isEqualTo(new AcademicError.CourseNotForClass(32L, CLASS_ID)));
        verify(grades, never()).save(any());
    }

    @Test
    void aCourseThatIsNotGradedCannotReceiveAGradeAndNothingIsStored() {
        CourseEntity club = course(33L, GRADE_LEVEL, "کارگاه");
        ReflectionTestUtils.setField(club, "graded", false);
        when(courses.findById(33L)).thenReturn(Optional.of(club));

        assertThatThrownBy(() -> facade.recordGrade(
                SCHOOL, STUDENT, CLASS_ID, "1405", GradingTerm.FIRST, null, new BigDecimal("12"), null, 33L))
                .isInstanceOfSatisfying(AcademicException.class, e ->
                        assertThat(e.error()).isEqualTo(new AcademicError.CourseNotGradable(33L)));
        verify(grades, never()).save(any());
    }

    @Test
    void theSchemeAndRangeRulesStillApplyToACourseGrade() {
        when(courses.findById(30L)).thenReturn(Optional.of(course(30L, GRADE_LEVEL, "ریاضی")));

        assertThatThrownBy(() -> facade.recordGrade(
                SCHOOL, STUDENT, CLASS_ID, "1405", GradingTerm.FIRST, null, new BigDecimal("21"), null, 30L))
                .isInstanceOfSatisfying(AcademicException.class, e ->
                        assertThat(e.error()).isInstanceOf(AcademicError.InvalidGrade.class));
        verify(grades, never()).save(any());
    }

    @Test
    void listingWithoutACourseReturnsOnlyOverallGradesAndWithACourseOnlyThatCourse() {
        GradeEntity overall = gradeRow(1L, null);
        GradeEntity math = gradeRow(2L, 30L);
        when(grades.findByClassIdAndAcademicYearAndTermAndCourseIdIsNull(CLASS_ID, "1405", GradingTerm.FIRST))
                .thenReturn(List.of(overall));
        when(grades.findByClassIdAndAcademicYearAndTermAndCourseId(CLASS_ID, "1405", GradingTerm.FIRST, 30L))
                .thenReturn(List.of(math));
        when(courses.findAllById(List.of(30L))).thenReturn(List.of(course(30L, GRADE_LEVEL, "ریاضی")));

        List<GradeInfo> overallRows = facade.listGradesForClass(SCHOOL, CLASS_ID, "1405", GradingTerm.FIRST, null);
        List<GradeInfo> mathRows = facade.listGradesForClass(SCHOOL, CLASS_ID, "1405", GradingTerm.FIRST, 30L);

        assertThat(overallRows).singleElement().satisfies(row -> {
            assertThat(row.courseId()).isNull();
            assertThat(row.courseName()).isNull();
        });
        assertThat(mathRows).singleElement().satisfies(row -> {
            assertThat(row.courseId()).isEqualTo(30L);
            assertThat(row.courseName()).isEqualTo("ریاضی");
        });
        // بدون درس، به‌دنبال نام درس هم نمی‌رود
        verify(courses).findAllById(List.of(30L));
    }

    @Test
    void studentGradesCarryTheirCourseNamesFromASingleLookup() {
        when(grades.findByStudentIdIn(List.of(STUDENT))).thenReturn(List.of(
                gradeRow(1L, null), gradeRow(2L, 30L), gradeRow(3L, 31L), gradeRow(4L, 30L)));
        when(courses.findAllById(List.of(30L, 31L))).thenReturn(List.of(
                course(30L, GRADE_LEVEL, "ریاضی"), course(31L, GRADE_LEVEL, "علوم")));

        List<GradeInfo> rows = facade.listGradesForStudents(SCHOOL, List.of(STUDENT));

        assertThat(rows).extracting(GradeInfo::courseName).containsExactly(null, "ریاضی", "علوم", "ریاضی");
        verify(courses).findAllById(any());
    }

    @Test
    void gradesOfACourseThatBecameNonGradedAreHiddenFromStudentReadsButKept() {
        CourseEntity retired = course(31L, GRADE_LEVEL, "علوم");
        ReflectionTestUtils.setField(retired, "graded", false);
        when(grades.findByStudentIdIn(List.of(STUDENT))).thenReturn(List.of(
                gradeRow(1L, null), gradeRow(2L, 30L), gradeRow(3L, 31L)));
        when(courses.findAllById(List.of(30L, 31L))).thenReturn(List.of(course(30L, GRADE_LEVEL, "ریاضی"), retired));

        List<GradeInfo> rows = facade.listGradesForStudents(SCHOOL, List.of(STUDENT));

        // نمره‌ی کل و درسِ نمره‌پذیر می‌مانند؛ درسِ غیرنمره‌پذیر پنهان است
        assertThat(rows).extracting(GradeInfo::courseName).containsExactly(null, "ریاضی");
        verify(grades, never()).delete(any());
    }

    @Test
    void coursesOfAClassAreTheGlobalCoursesOfItsGradeLevel() {
        when(courses.findByGradeLevelAndGradedTrueOrderByNameAsc(GRADE_LEVEL)).thenReturn(List.of(
                course(30L, GRADE_LEVEL, "ریاضی"), course(31L, GRADE_LEVEL, "علوم")));

        List<CourseInfo> result = facade.listCoursesForClass(SCHOOL, CLASS_ID);

        assertThat(result).extracting(c -> c.id().value()).containsExactly(30L, 31L);
        assertThat(result).extracting(CourseInfo::name).containsExactly("ریاضی", "علوم");
    }

    @Test
    void coursesOfAnotherSchoolsClassAreNotListed() {
        assertThatThrownBy(() -> facade.listCoursesForClass(SCHOOL + 1, CLASS_ID))
                .isInstanceOfSatisfying(AcademicException.class, e ->
                        assertThat(e.error()).isEqualTo(new AcademicError.ClassNotFound(CLASS_ID)));
        verify(courses, never()).findByGradeLevelAndGradedTrueOrderByNameAsc(org.mockito.ArgumentMatchers.anyInt());
    }

    private static GradeEntity gradeRow(Long id, Long courseId) {
        GradeEntity grade = new GradeEntity();
        grade.setId(id);
        grade.setSchoolId(SCHOOL);
        grade.setStudentId(STUDENT);
        grade.setClassId(CLASS_ID);
        grade.setCourseId(courseId);
        grade.setAcademicYear("1405");
        grade.setTerm(GradingTerm.FIRST);
        grade.setScheme(GradingScheme.NUMERIC);
        grade.setNumericScore(new BigDecimal("15"));
        grade.setRecordedAt(Instant.parse("2026-10-02T10:00:00Z"));
        return grade;
    }
}
