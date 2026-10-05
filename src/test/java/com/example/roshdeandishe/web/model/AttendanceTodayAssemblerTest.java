package com.example.roshdeandishe.web.model;

import com.example.roshdeandishe.attendance.AttendanceFacade;
import com.example.roshdeandishe.attendance.AttendanceFacade.DaySummary;
import com.example.roshdeandishe.attendance.shared.AttendanceError;
import com.example.roshdeandishe.attendance.shared.AttendanceException;
import com.example.roshdeandishe.iam.AccessContext;
import com.example.roshdeandishe.iam.AccessDecision;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.ModuleCode;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.SchoolFacade.SchoolInfo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * گاردِ «حضور و غیاب امروز» پیشخوان بدون Spring و Docker: مدرسه‌ای که ماژول {@code ATTENDANCE} را ندارد
 * هیچ بخشی نمی‌گیرد و AttendanceFacade اصلاً صدا زده نمی‌شود (facade خودش entitlement نمی‌سنجد).
 */
class AttendanceTodayAssemblerTest {

    private static final UserId ADMIN = new UserId(3L);

    private final IamFacade iam = mock(IamFacade.class);
    private final AttendanceFacade attendance = mock(AttendanceFacade.class);
    private final AttendanceTodayAssembler assembler = new AttendanceTodayAssembler(iam, attendance);

    private void givenEntitlement(boolean granted) {
        when(iam.checkAccess(any(UserId.class), any(ModuleCode.class), any(AccessContext.class)))
                .thenReturn(granted ? new AccessDecision.Granted("ok") : new AccessDecision.Denied("no"));
    }

    private static Optional<SchoolInfo> school(String timezone) {
        return Optional.of(new SchoolInfo(new SchoolId(7L), "دبستان مهر", null, timezone, null, null, null, null));
    }

    @Test
    void aSchoolWithoutTheAttendanceModuleGetsNoRowAndTheFacadeIsNotCalled() {
        givenEntitlement(false);

        assertThat(assembler.forAdminSchool(ADMIN, 7L, "دبستان مهر", school("Asia/Tehran"))).isEmpty();

        verify(attendance, never()).summaryForDay(anyLong(), any());
    }

    @Test
    void anEntitledSchoolGetsItsSummaryForTodayInTheSchoolsTimezone() {
        givenEntitlement(true);
        when(attendance.summaryForDay(anyLong(), any())).thenAnswer(invocation ->
                new DaySummary(invocation.getArgument(1), true, 4, 1, 100, 96, 6, 8));

        AttendanceTodayRow row = assembler.forAdminSchool(ADMIN, 7L, "دبستان مهر", school("Asia/Tehran")).orElseThrow();

        assertThat(row.schoolId()).isEqualTo(7L);
        assertThat(row.schoolName()).isEqualTo("دبستان مهر");
        assertThat(row.schoolDay()).isTrue();
        assertThat(row.absent()).isEqualTo(4);
        assertThat(row.presentPercent()).isEqualTo(96);
        assertThat(row.classesWithoutRoll()).isEqualTo(2);
        verify(attendance).summaryForDay(7L, LocalDate.now(ZoneId.of("Asia/Tehran")));
    }

    @Test
    void anUnknownTimezoneFallsBackToTheServersInsteadOfFailing() {
        assertThat(AttendanceTodayAssembler.today(school("Not/AZone"))).isEqualTo(LocalDate.now(ZoneId.systemDefault()));
        assertThat(AttendanceTodayAssembler.today(school(null))).isEqualTo(LocalDate.now(ZoneId.systemDefault()));
        assertThat(AttendanceTodayAssembler.today(Optional.empty())).isEqualTo(LocalDate.now(ZoneId.systemDefault()));
    }

    @Test
    void aDomainErrorFromTheSummaryNeverBreaksTheDashboard() {
        givenEntitlement(true);
        when(attendance.summaryForDay(anyLong(), any()))
                .thenThrow(new AttendanceException(new AttendanceError.SchoolNotFound(7L)));

        assertThat(assembler.forAdminSchool(ADMIN, 7L, "دبستان مهر", school("Asia/Tehran"))).isEmpty();
    }
}
