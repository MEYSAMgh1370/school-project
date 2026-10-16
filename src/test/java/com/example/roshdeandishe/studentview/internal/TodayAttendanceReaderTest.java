package com.example.roshdeandishe.studentview.internal;

import com.example.roshdeandishe.attendance.AttendanceFacade;
import com.example.roshdeandishe.attendance.AttendanceFacade.ClassDay;
import com.example.roshdeandishe.attendance.AttendanceFacade.StudentMark;
import com.example.roshdeandishe.attendance.AttendanceStatus;
import com.example.roshdeandishe.attendance.shared.AttendanceError;
import com.example.roshdeandishe.attendance.shared.AttendanceException;
import com.example.roshdeandishe.iam.AccessContext;
import com.example.roshdeandishe.iam.AccessDecision;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.ModuleCode;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.studentview.TodayAttendance;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * وضعیتِ امروزِ فرزند روی کارتِ والد، بدون Spring و Docker: ثبت‌نشده هرگز «حاضر» نیست، فقط ردیفِ همین فرزند
 * خوانده می‌شود، و بدونِ ماژول/کلاس یا با خطا چیزی نشان داده نمی‌شود.
 */
class TodayAttendanceReaderTest {

    private static final UserId PARENT = new UserId(9L);
    private static final long SCHOOL = 7L;
    private static final long CLASS = 10L;
    private static final long CHILD = 100L;

    private final IamFacade iam = mock(IamFacade.class);
    private final AttendanceFacade attendance = mock(AttendanceFacade.class);
    private final SchoolFacade school = mock(SchoolFacade.class);
    private final TodayAttendanceReader resolver = new TodayAttendanceReader(iam, attendance, school);

    private void givenEntitlement(boolean granted) {
        when(iam.checkAccess(any(UserId.class), any(ModuleCode.class), any(AccessContext.class)))
                .thenReturn(granted ? new AccessDecision.Granted("ok") : new AccessDecision.Denied("no"));
        when(school.findSchool(any())).thenReturn(Optional.empty());
    }

    private void givenDay(boolean recordable, boolean taken, StudentMark... marks) {
        when(attendance.getClassDay(anyLong(), anyLong(), any(LocalDate.class))).thenReturn(
                new ClassDay(CLASS, "اول الف", LocalDate.now(), taken, recordable, List.of(marks)));
    }

    private TodayAttendance today() {
        return resolver.todayFor(PARENT, SCHOOL, CLASS, CHILD);
    }

    @Test
    void aRollNotTakenYetIsPendingNeverPresent() {
        givenEntitlement(true);
        givenDay(true, false, new StudentMark(CHILD, "فرزند", null, false, null));

        assertThat(today()).isEqualTo(TodayAttendance.PENDING);
    }

    @Test
    void aNonSchoolDayIsReportedAsSuch() {
        givenEntitlement(true);
        givenDay(false, false);

        assertThat(today()).isEqualTo(TodayAttendance.NOT_SCHOOL_DAY);
    }

    @Test
    void aTakenRollGivesTheChildsOwnMarkOnly() {
        givenEntitlement(true);
        givenDay(true, true,
                new StudentMark(1L, "همکلاسی", AttendanceStatus.ABSENT, false, null),
                new StudentMark(CHILD, "فرزند", null, false, null));
        assertThat(today()).isEqualTo(TodayAttendance.PRESENT);

        givenDay(true, true, new StudentMark(CHILD, "فرزند", AttendanceStatus.LATE, false, null));
        assertThat(today()).isEqualTo(TodayAttendance.LATE);

        givenDay(true, true, new StudentMark(CHILD, "فرزند", AttendanceStatus.ABSENT, false, null));
        assertThat(today()).isEqualTo(TodayAttendance.ABSENT);

        givenDay(true, true, new StudentMark(CHILD, "فرزند", AttendanceStatus.ABSENT, true, null));
        assertThat(today()).isEqualTo(TodayAttendance.ABSENT_EXCUSED);
    }

    @Test
    void aChildMissingFromTheRollShowsNothing() {
        givenEntitlement(true);
        givenDay(true, true, new StudentMark(1L, "همکلاسی", null, false, null));

        assertThat(today()).isEqualTo(TodayAttendance.UNAVAILABLE);
    }

    @Test
    void noModuleNoClassOrAFailureShowsNothingAndSkipsTheFacadeWhereItShould() {
        givenEntitlement(false);
        assertThat(today()).isEqualTo(TodayAttendance.UNAVAILABLE);
        verify(attendance, never()).getClassDay(anyLong(), anyLong(), any());

        assertThat(resolver.todayFor(PARENT, SCHOOL, null, CHILD)).isEqualTo(TodayAttendance.UNAVAILABLE);

        givenEntitlement(true);
        when(attendance.getClassDay(anyLong(), anyLong(), any(LocalDate.class)))
                .thenThrow(new AttendanceException(new AttendanceError.NotYourClass(CLASS)));
        assertThat(today()).isEqualTo(TodayAttendance.UNAVAILABLE);
    }
}
