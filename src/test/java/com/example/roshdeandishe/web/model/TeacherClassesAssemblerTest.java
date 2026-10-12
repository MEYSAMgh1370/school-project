package com.example.roshdeandishe.web.model;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.AcademicFacade.ClassId;
import com.example.roshdeandishe.academic.AcademicFacade.ClassInfo;
import com.example.roshdeandishe.academic.AcademicFacade.StudentId;
import com.example.roshdeandishe.academic.AcademicFacade.StudentInfo;
import com.example.roshdeandishe.academic.GradingScheme;
import com.example.roshdeandishe.academic.StudentStatus;
import com.example.roshdeandishe.academic.TimetableFacade;
import com.example.roshdeandishe.attendance.AttendanceFacade;
import com.example.roshdeandishe.attendance.AttendanceFacade.RecordableClass;
import com.example.roshdeandishe.attendance.shared.AttendanceError;
import com.example.roshdeandishe.attendance.shared.AttendanceException;
import com.example.roshdeandishe.iam.AccessContext;
import com.example.roshdeandishe.iam.AccessDecision;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.ModuleCode;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.web.model.TeacherClassRow.AttendanceState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * «کلاس‌های من» پیشخوانِ معلم بدون Spring و Docker: با ماژولِ حضور و غیاب وضعیتِ امروز می‌آید (ثبت شده/نشده)،
 * بدونِ آن فقط فهرستِ کلاس‌های معلم بدونِ وضعیت، و شمارشِ دانش‌آموز فقط فعال‌های همان کلاس است.
 */
class TeacherClassesAssemblerTest {

    private static final UserId TEACHER = new UserId(5L);
    private static final long SCHOOL = 7L;

    private final IamFacade iam = mock(IamFacade.class);
    private final AcademicFacade academic = mock(AcademicFacade.class);
    private final TimetableFacade timetable = mock(TimetableFacade.class);
    private final AttendanceFacade attendance = mock(AttendanceFacade.class);
    private final TeacherClassesAssembler assembler = new TeacherClassesAssembler(iam, academic, timetable, attendance);

    private void givenEntitlement(boolean granted) {
        when(iam.checkAccess(any(UserId.class), any(ModuleCode.class), any(AccessContext.class)))
                .thenReturn(granted ? new AccessDecision.Granted("ok") : new AccessDecision.Denied("no"));
    }

    private void givenStudents(long classId) {
        when(academic.listStudentsInClass(anyLong(), anyLong())).thenReturn(List.of(
                new StudentInfo(new StudentId(1L), 11L, SCHOOL, new ClassId(classId), StudentStatus.ACTIVE),
                new StudentInfo(new StudentId(2L), 12L, SCHOOL, new ClassId(classId), StudentStatus.ACTIVE),
                // قبلاً در این کلاس بوده و حالا جای دیگری است - شمرده نمی‌شود
                new StudentInfo(new StudentId(3L), 13L, SCHOOL, new ClassId(99L), StudentStatus.ACTIVE),
                new StudentInfo(new StudentId(4L), 14L, SCHOOL, null, StudentStatus.GRADUATED)));
    }

    @Test
    void anEntitledSchoolShowsTodaysAttendanceStateForEachClassTheTeacherMayRecord() {
        givenEntitlement(true);
        givenStudents(10L);
        when(attendance.listRecordableClasses(SCHOOL, 5L, false, null)).thenReturn(List.of(
                new RecordableClass(10L, "اول الف", null, 1, false),
                new RecordableClass(11L, "دوم ب", "ب", 2, true)));

        TeacherClassesRow row = assembler.forTeacherSchool(TEACHER, SCHOOL, "دبستان مهر");

        assertThat(row.schoolName()).isEqualTo("دبستان مهر");
        assertThat(row.classes()).extracting(TeacherClassRow::name).containsExactly("اول الف", "دوم ب");
        assertThat(row.classes().get(0).pending()).isTrue();
        assertThat(row.classes().get(1).taken()).isTrue();
        // فقط دانش‌آموزِ فعالِ همان کلاس شمرده می‌شود
        assertThat(row.classes().get(0).studentCount()).isEqualTo(2);
        verify(timetable, never()).listClassesTaughtBy(anyLong(), anyLong());
    }

    @Test
    void aSchoolWithoutTheAttendanceModuleListsTheClassesWithoutAState() {
        givenEntitlement(false);
        givenStudents(10L);
        when(timetable.listClassesTaughtBy(SCHOOL, 5L)).thenReturn(List.of(
                new ClassInfo(new ClassId(10L), SCHOOL, "اول الف", 5L, null, 1, GradingScheme.DESCRIPTIVE)));

        TeacherClassesRow row = assembler.forTeacherSchool(TEACHER, SCHOOL, "دبستان مهر");

        assertThat(row.classes()).hasSize(1);
        assertThat(row.classes().get(0).attendance()).isEqualTo(AttendanceState.UNAVAILABLE);
        verify(attendance, never()).listRecordableClasses(anyLong(), anyLong(), anyBoolean(), any());
    }

    @Test
    void aTeacherWithNoClassesGetsAnEmptyRowSoThePageCanSaySo() {
        givenEntitlement(true);
        when(attendance.listRecordableClasses(SCHOOL, 5L, false, null)).thenReturn(List.of());

        assertThat(assembler.forTeacherSchool(TEACHER, SCHOOL, "دبستان مهر").empty()).isTrue();
    }

    @Test
    void anAttendanceFailureFallsBackToTheClassListInsteadOfBreakingTheDashboard() {
        givenEntitlement(true);
        givenStudents(10L);
        when(attendance.listRecordableClasses(anyLong(), anyLong(), anyBoolean(), any()))
                .thenThrow(new AttendanceException(new AttendanceError.NotYourClass(10L)));
        when(timetable.listClassesTaughtBy(SCHOOL, 5L)).thenReturn(List.of(
                new ClassInfo(new ClassId(10L), SCHOOL, "اول الف", 5L, null, 1, GradingScheme.DESCRIPTIVE)));

        TeacherClassesRow row = assembler.forTeacherSchool(TEACHER, SCHOOL, "دبستان مهر");

        assertThat(row.classes()).hasSize(1);
        assertThat(row.classes().get(0).attendance()).isEqualTo(AttendanceState.UNAVAILABLE);
    }
}
