package com.example.roshdeandishe.studentview.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.AcademicFacade.ClassId;
import com.example.roshdeandishe.academic.AcademicFacade.GradeId;
import com.example.roshdeandishe.academic.AcademicFacade.GradeInfo;
import com.example.roshdeandishe.academic.AcademicFacade.StudentId;
import com.example.roshdeandishe.academic.DescriptiveGradeLevel;
import com.example.roshdeandishe.academic.GradingScheme;
import com.example.roshdeandishe.academic.GradingTerm;
import com.example.roshdeandishe.attendance.AttendanceFacade;
import com.example.roshdeandishe.attendance.AttendanceFacade.MyDayAttendance;
import com.example.roshdeandishe.attendance.AttendanceFacade.MyStudentAttendance;
import com.example.roshdeandishe.attendance.AttendanceStatus;
import com.example.roshdeandishe.exam.ExamFacade;
import com.example.roshdeandishe.exam.ExamFacade.ExamId;
import com.example.roshdeandishe.exam.ExamFacade.ExamInfo;
import com.example.roshdeandishe.exam.ExamType;
import com.example.roshdeandishe.iam.AccessDecision;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearId;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearInfo;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.studentview.StudentSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** خلاصهٔ پیشخوان (نمره‌ها، آزمون‌ها، غیبت) بدونِ Spring: سقفِ ۳ ردیف، فقط سالِ جاری، و غیبتِ ثبت‌نشده شمرده نمی‌شود. */
class StudentSummaryReaderTest {

    private static final UserId VIEWER = new UserId(10L);
    private static final Long SCHOOL = 5L;
    private static final Long STUDENT = 7L;

    private final IamFacade iam = mock(IamFacade.class);
    private final SchoolFacade school = mock(SchoolFacade.class);
    private final AcademicFacade academic = mock(AcademicFacade.class);
    private final ExamFacade exam = mock(ExamFacade.class);
    private final AttendanceFacade attendance = mock(AttendanceFacade.class);
    private final StudentSummaryReader assembler =
            new StudentSummaryReader(iam, school, academic, exam, attendance);

    private void givenYear() {
        when(school.findCurrentAcademicYear(new SchoolId(SCHOOL))).thenReturn(Optional.of(
                new AcademicYearInfo(new AcademicYearId(1L), "1405-1406", 1405,
                        LocalDate.now().minusDays(100), LocalDate.now().plusDays(100))));
        when(school.formatSolarDate(any())).thenReturn("۱ مهر ۱۴۰۵");
        when(iam.checkAccess(any(), any(), any())).thenReturn(new AccessDecision.Denied("no"));
    }

    private static GradeInfo grade(long id, String year, String course, BigDecimal score, long epoch) {
        return new GradeInfo(new GradeId(id), SCHOOL, new StudentId(STUDENT), new ClassId(1L), year,
                GradingTerm.FIRST, GradingScheme.NUMERIC, null, score, null, 1L, Instant.ofEpochSecond(epoch),
                1L, course);
    }

    @Test
    void showsTheThreeLatestGradesOfTheCurrentYearOnly() {
        givenYear();
        when(academic.listGradesForStudents(SCHOOL, List.of(STUDENT))).thenReturn(List.of(
                grade(1, "1405-1406", "ریاضی", new BigDecimal("18.50"), 100),
                grade(2, "1405-1406", "علوم", new BigDecimal("17"), 200),
                grade(3, "1404-1405", "قدیمی", new BigDecimal("10"), 900),
                grade(4, "1405-1406", "فارسی", new BigDecimal("19"), 300),
                grade(5, "1405-1406", "هنر", new BigDecimal("20"), 400)));

        StudentSummary summary = assembler.forStudent(VIEWER, SCHOOL, STUDENT);

        assertThat(summary.grades()).extracting(StudentSummary.GradeLine::subject)
                .containsExactly("هنر", "فارسی", "علوم");
        assertThat(summary.grades().get(2).value()).isEqualTo("17");
    }

    @Test
    void aDescriptiveGradeShowsItsLevelAndNoGradeCourseIsTheOverallGrade() {
        givenYear();
        when(academic.listGradesForStudents(SCHOOL, List.of(STUDENT))).thenReturn(List.of(new GradeInfo(
                new GradeId(1L), SCHOOL, new StudentId(STUDENT), new ClassId(1L), "1405-1406", GradingTerm.SECOND,
                GradingScheme.DESCRIPTIVE, DescriptiveGradeLevel.GOOD, null, null, 1L, Instant.now(), null, null)));

        StudentSummary.GradeLine line = assembler.forStudent(VIEWER, SCHOOL, STUDENT).grades().get(0);

        assertThat(line.subject()).isEqualTo("نمره‌ی کل");
        assertThat(line.value()).isEqualTo("خوب");
        assertThat(line.term()).isEqualTo("نوبت دوم");
    }

    @Test
    void listsOnlyExamsThatHaveNotEndedSoonestFirstUpToThree() {
        givenYear();
        LocalDateTime now = LocalDateTime.now();
        when(exam.listExamsForStudent(SCHOOL, STUDENT)).thenReturn(List.of(
                examInfo(1, "گذشته", now.minusDays(3), now.minusDays(3).plusHours(1)),
                examInfo(2, "دور", now.plusDays(9), now.plusDays(9).plusHours(1)),
                examInfo(3, "نزدیک", now.plusDays(1), now.plusDays(1).plusHours(1)),
                examInfo(4, "میانی", now.plusDays(4), now.plusDays(4).plusHours(1)),
                examInfo(5, "دورتر", now.plusDays(20), now.plusDays(20).plusHours(1))));

        assertThat(assembler.forStudent(VIEWER, SCHOOL, STUDENT).exams())
                .extracting(StudentSummary.ExamLine::title).containsExactly("نزدیک", "میانی", "دور");
    }

    private static ExamInfo examInfo(long id, String title, LocalDateTime start, LocalDateTime end) {
        return new ExamInfo(new ExamId(id), SCHOOL, ExamType.WEEKLY, title, 1L, STUDENT, start, end, 60, null,
                List.of());
    }

    @Test
    void countsOnlyRecordedAbsencesSplitIntoUnexcusedExcusedAndLate() {
        givenYear();
        when(iam.checkAccess(any(), any(), any())).thenReturn(new AccessDecision.Granted("ok"));
        LocalDate d = LocalDate.now().minusDays(10);
        when(attendance.listMyAttendance(anyLong(), anyLong(), any(), any())).thenReturn(List.of(
                new MyStudentAttendance(STUDENT, "ی", true, List.of(
                        new MyDayAttendance(d, AttendanceStatus.ABSENT, false, null),
                        new MyDayAttendance(d.plusDays(1), AttendanceStatus.ABSENT, true, null),
                        new MyDayAttendance(d.plusDays(2), AttendanceStatus.LATE, false, null))),
                new MyStudentAttendance(99L, "دیگری", false, List.of(
                        new MyDayAttendance(d, AttendanceStatus.ABSENT, false, null)))));

        assertThat(assembler.forStudent(VIEWER, SCHOOL, STUDENT).attendance())
                .isEqualTo(new StudentSummary.AttendanceCount(1, 1, 1));
    }

    @Test
    void noAttendanceRowWhenTheSchoolLacksTheModuleOrHasNoCurrentYear() {
        givenYear();
        when(iam.checkAccess(any(), any(), any())).thenReturn(new AccessDecision.Denied("no"));
        assertThat(assembler.forStudent(VIEWER, SCHOOL, STUDENT).hasAttendance()).isFalse();

        when(school.findCurrentAcademicYear(new SchoolId(SCHOOL))).thenReturn(Optional.empty());
        when(iam.checkAccess(any(), any(), any())).thenReturn(new AccessDecision.Granted("ok"));
        assertThat(assembler.forStudent(VIEWER, SCHOOL, STUDENT).hasAttendance()).isFalse();
    }
}
