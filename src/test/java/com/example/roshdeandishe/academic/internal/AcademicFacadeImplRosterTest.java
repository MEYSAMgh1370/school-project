package com.example.roshdeandishe.academic.internal;

import com.example.roshdeandishe.academic.AcademicFacade.StudentInfo;
import com.example.roshdeandishe.academic.GradingScheme;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.school.SchoolFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * فهرست دانش‌آموزانِ یک کلاس در یک سال (AcademicFacade.listStudentsInClass با academicYear) را بدون Spring و
 * Docker می‌آزماید: اجتماعِ «کلاسِ جاریِ دانش‌آموز همین کلاس است» و «در همان سال در همین کلاس ثبت‌نام داشته»،
 * بدون تکرار.
 */
class AcademicFacadeImplRosterTest {

    private static final Long SCHOOL = 5L;
    private static final Long OTHER_SCHOOL = 6L;
    private static final Long CLASS_ID = 7L;
    private static final Long OTHER_CLASS = 8L;
    private static final String YEAR = "1404-1405";

    private static final Long CURRENT_ONLY = 101L;
    private static final Long ENROLLED_ONLY = 102L;
    private static final Long BOTH = 103L;
    private static final Long ELSEWHERE = 104L;
    private static final Long FOREIGN_SCHOOL = 105L;

    private StudentRepository students;
    private EnrollmentRepository enrollments;
    private AcademicFacadeImpl facade;

    @BeforeEach
    void setUp() {
        students = mock(StudentRepository.class);
        ClassRepository classes = mock(ClassRepository.class);
        enrollments = mock(EnrollmentRepository.class);
        facade = new AcademicFacadeImpl(students, classes, mock(StudentGuardianRepository.class),
                mock(GradeRepository.class), mock(CourseRepository.class), enrollments, mock(EnrollmentLedger.class),
                mock(IamFacade.class), mock(SchoolFacade.class), mock(ApplicationEventPublisher.class));

        ClassEntity classEntity = new ClassEntity(SCHOOL, "پنجم الف", 9L, "الف", 5, GradingScheme.NUMERIC);
        ReflectionTestUtils.setField(classEntity, "id", CLASS_ID);
        when(classes.findById(CLASS_ID)).thenReturn(Optional.of(classEntity));

        StudentEntity currentOnly = student(CURRENT_ONLY, SCHOOL, CLASS_ID);
        // کلاسِ جاری‌اش دیگر این کلاس نیست، ولی در سالِ پرسیده‌شده در آن ثبت‌نام داشته
        StudentEntity enrolledOnly = student(ENROLLED_ONLY, SCHOOL, OTHER_CLASS);
        // هم کلاسِ جاری و هم ثبت‌نامِ همان سال: باید یک بار بیاید
        StudentEntity both = student(BOTH, SCHOOL, CLASS_ID);
        StudentEntity elsewhere = student(ELSEWHERE, SCHOOL, OTHER_CLASS);
        // ثبت‌نامش به این کلاس اشاره می‌کند ولی دانش‌آموزِ مدرسه‌ی دیگری است
        StudentEntity foreign = student(FOREIGN_SCHOOL, OTHER_SCHOOL, OTHER_CLASS);

        when(students.findBySchoolId(SCHOOL)).thenReturn(List.of(currentOnly, enrolledOnly, both, elsewhere));
        // مثل JPA واقعی: findAllById فقط شناسه‌های خواسته‌شده را برمی‌گرداند
        when(students.findAllById(any())).thenAnswer(invocation -> {
            Iterable<Long> ids = invocation.getArgument(0);
            List<StudentEntity> all = List.of(currentOnly, enrolledOnly, both, elsewhere, foreign);
            return all.stream().filter(s -> {
                for (Long id : ids) {
                    if (id.equals(s.getId())) {
                        return true;
                    }
                }
                return false;
            }).toList();
        });

        when(enrollments.findByClassIdAndAcademicYear(CLASS_ID, YEAR)).thenReturn(List.of(
                enrollment(ENROLLED_ONLY, SCHOOL),
                enrollment(BOTH, SCHOOL),
                enrollment(FOREIGN_SCHOOL, OTHER_SCHOOL)));
    }

    private static StudentEntity student(Long id, Long schoolId, Long classId) {
        StudentEntity student = new StudentEntity();
        student.setId(id);
        student.setSchoolId(schoolId);
        student.setClassId(classId);
        return student;
    }

    private static EnrollmentEntity enrollment(Long studentId, Long schoolId) {
        return new EnrollmentEntity(schoolId, studentId, YEAR, CLASS_ID, 5, EnrollmentEntity.Status.ENROLLED, null);
    }

    private static List<Long> idsOf(List<StudentInfo> infos) {
        return infos.stream().map(info -> info.id().value()).toList();
    }

    @Test
    void rosterIsTheUnionOfCurrentClassAndYearEnrollmentWithoutDuplicates() {
        List<Long> ids = idsOf(facade.listStudentsInClass(SCHOOL, CLASS_ID, YEAR));

        assertThat(ids).containsExactlyInAnyOrder(CURRENT_ONLY, ENROLLED_ONLY, BOTH);
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void theYearIsCanonicalizedBeforeTheEnrollmentLookupAndOtherSchoolsAreExcluded() {
        List<Long> ids = idsOf(facade.listStudentsInClass(SCHOOL, CLASS_ID, " ۱۴۰۴-۱۴۰۵ "));

        verify(enrollments).findByClassIdAndAcademicYear(CLASS_ID, YEAR);
        assertThat(ids).contains(ENROLLED_ONLY).doesNotContain(FOREIGN_SCHOOL, ELSEWHERE);
    }
}
