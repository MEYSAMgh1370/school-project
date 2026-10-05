package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.AcademicFacade.ClassId;
import com.example.roshdeandishe.academic.AcademicFacade.StudentId;
import com.example.roshdeandishe.academic.AcademicFacade.StudentInfo;
import com.example.roshdeandishe.attendance.AbsenceRecordedEvent;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.iam.IamFacade.UserProfile;
import com.example.roshdeandishe.notification.NotificationFacade;
import com.example.roshdeandishe.platform.TenantContext;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.SchoolFacade.SchoolInfo;
import com.example.roshdeandishe.school.SchoolFacade.SchoolLevel;
import com.example.roshdeandishe.school.SchoolFacade.SchoolStatus;
import com.example.roshdeandishe.school.SchoolFacade.WeekStart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * پیامِ غیبت به ولی‌ها (attendance.md «Notifications») بدون Spring و Docker: متنِ پیام، رد‌شدنِ بی‌صدای ولیِ بدونِ
 * حسابِ بله، و اینکه شکستِ یک ارسال نه بقیه را متوقف می‌کند و نه از listener بیرون می‌زند.
 */
class AbsenceRecordedEventListenerTest {

    private static final Long SCHOOL = 5L;
    private static final Long STUDENT = 11L;
    private static final Long STUDENT_USER = 1011L;
    private static final LocalDate DAY = LocalDate.of(2024, 10, 7);

    private AcademicFacade academic;
    private IamFacade iam;
    private SchoolFacade school;

    @BeforeEach
    void setUp() {
        academic = mock(AcademicFacade.class);
        iam = mock(IamFacade.class);
        school = mock(SchoolFacade.class);
        when(academic.findStudentInfo(STUDENT)).thenReturn(Optional.of(
                new StudentInfo(new StudentId(STUDENT), STUDENT_USER, SCHOOL, new ClassId(7L))));
        when(iam.findUser(new UserId(STUDENT_USER))).thenReturn(Optional.of(
                new UserProfile(new UserId(STUDENT_USER), "s@x.test", "علی رضایی", null)));
        when(school.findSchool(new SchoolId(SCHOOL))).thenReturn(Optional.of(new SchoolInfo(new SchoolId(SCHOOL),
                "دبستان رشد", SchoolStatus.ACTIVE, "Asia/Tehran", "fa-IR", SchoolLevel.ELEMENTARY, null,
                WeekStart.SATURDAY)));
    }

    private static AbsenceRecordedEvent event() {
        return new AbsenceRecordedEvent(SCHOOL, 7L, STUDENT, DAY, 90L, Instant.EPOCH);
    }

    @Test
    void theMessageNamesTheStudentTheSchoolAndTheIsoDate() {
        assertThat(AbsenceRecordedEventListener.buildMessage("علی رضایی", "دبستان رشد", DAY))
                .isEqualTo("غیبت: علی رضایی در مدرسه‌ی دبستان رشد در تاریخ 2024-10-07 غایب ثبت شد.");
        // نامِ ناشناخته و مدرسه‌ی بی‌نام: متن همچنان معنا دارد
        assertThat(AbsenceRecordedEventListener.buildMessage(null, " ", DAY))
                .isEqualTo("غیبت: فرزند شما در تاریخ 2024-10-07 غایب ثبت شد.");
    }

    @Test
    void everyLinkedParentIsMessagedAndOnlyThoseWithABaleAccountReceiveIt() {
        when(academic.listGuardians(SCHOOL, STUDENT)).thenReturn(List.of(500L, 501L));
        // ولیِ ۵۰۰ بله را لینک کرده، ۵۰۱ نه: کانال برای او false می‌دهد و بی‌صدا رد می‌شود
        List<String> delivered = new ArrayList<>();
        NotificationChannel bale = (userId, text) -> {
            if (userId.equals(500L)) {
                delivered.add(userId + ":" + text);
                return true;
            }
            return false;
        };
        NotificationFacade notifications = new NotificationFacadeImpl(List.of(bale), null);

        new AbsenceRecordedEventListener(academic, iam, school, notifications).onAbsenceRecorded(event());

        assertThat(delivered).containsExactly("500:" + AbsenceRecordedEventListener
                .buildMessage("علی رضایی", "دبستان رشد", DAY));
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void aFailingSendDoesNotStopTheOtherParentsAndNeverEscapesTheListener() {
        when(academic.listGuardians(SCHOOL, STUDENT)).thenReturn(List.of(500L, 501L));
        NotificationFacade notifications = mock(NotificationFacade.class);
        when(notifications.sendMessageToUser(eq(500L), anyString())).thenThrow(new IllegalStateException("bale down"));

        new AbsenceRecordedEventListener(academic, iam, school, notifications).onAbsenceRecorded(event());

        verify(notifications).sendMessageToUser(eq(501L), anyString());
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void aStudentWithoutGuardiansOrWithoutARecordSendsNothing() {
        NotificationFacade notifications = mock(NotificationFacade.class);
        AbsenceRecordedEventListener listener = new AbsenceRecordedEventListener(academic, iam, school, notifications);

        when(academic.listGuardians(SCHOOL, STUDENT)).thenReturn(List.of());
        listener.onAbsenceRecorded(event());

        when(academic.findStudentInfo(STUDENT)).thenReturn(Optional.empty());
        listener.onAbsenceRecorded(event());

        verify(notifications, never()).sendMessageToUser(anyLong(), anyString());
        assertThat(TenantContext.isSet()).isFalse();
    }
}
