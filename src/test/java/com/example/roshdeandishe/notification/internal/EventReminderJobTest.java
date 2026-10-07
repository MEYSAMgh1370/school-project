package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.notification.NotificationFacade;
import com.example.roshdeandishe.platform.TenantContext;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.ReminderEvent;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.SchoolFacade.SchoolInfo;
import com.example.roshdeandishe.school.SchoolFacade.SchoolLevel;
import com.example.roshdeandishe.school.SchoolFacade.SchoolStatus;
import com.example.roshdeandishe.school.SchoolFacade.WeekStart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * کارِ یادآوریِ رویدادها (event-reminders.md) بدون Spring و Docker: ساعتِ ارسال و timezone مدرسه، catch-up،
 * فیلترِ نقش، دفترِ ضدِ تکرار، و ایزوله‌بودنِ شکست‌ها.
 */
class EventReminderJobTest {

    private static final long SCHOOL = 5L;
    private static final long EVENT = 100L;
    // تهران UTC+03:30 است (بدونِ ساعتِ تابستانی)؛ روزِ محلی در هر دو لحظه 2026-10-06 است
    private static final Instant TEHRAN_0830 = Instant.parse("2026-10-06T05:00:00Z");
    private static final Instant TEHRAN_0730 = Instant.parse("2026-10-06T04:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);

    private SchoolFacade school;
    private IamFacade iam;
    private NotificationFacade notification;
    private Set<String> claimed;
    private EventReminderSentRepository ledger;

    @BeforeEach
    void setUp() {
        school = mock(SchoolFacade.class);
        iam = mock(IamFacade.class);
        notification = mock(NotificationFacade.class);
        claimed = new HashSet<>();
        ledger = (entryId, userId) -> claimed.add(entryId + ":" + userId) ? 1 : 0;
        when(school.formatSolarDate(any())).thenReturn("۱۴ مهر ۱۴۰۵");
        when(school.reminderRoleCodes(any())).thenReturn(Optional.empty());
        when(notification.sendMessageToUser(anyLong(), anyString())).thenReturn(true);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private EventReminderJob job() {
        return new EventReminderJob(school, iam, notification, ledger, true, 8);
    }

    private static SchoolInfo info(long id, SchoolStatus status, String tz) {
        return new SchoolInfo(new SchoolId(id), "دبستان " + id, status, tz, "fa-IR", SchoolLevel.ELEMENTARY, null,
                WeekStart.SATURDAY);
    }

    private static MembershipInfo member(long userId, String role, boolean active) {
        return new MembershipInfo(userId * 10, new UserId(userId), "u@x.test", "نام", SCHOOL, role, active, true);
    }

    private void oneSchool(String tz) {
        when(school.listSchools()).thenReturn(List.of(info(SCHOOL, SchoolStatus.ACTIVE, tz)));
    }

    private void dueToday() {
        when(school.dueReminderEvents(new SchoolId(SCHOOL), TODAY))
                .thenReturn(List.of(new ReminderEvent(EVENT, TODAY, "جشن", 0)));
    }

    private void members(MembershipInfo... members) {
        when(iam.findMembershipsBySchool(SCHOOL)).thenReturn(List.of(members));
    }

    // ---- ساعت و timezone ----

    @Test
    void nothingIsSentBeforeTheSendHour() {
        oneSchool("Asia/Tehran");
        dueToday();
        members(member(1, "PARENT", true));

        job().runOnce(TEHRAN_0730);

        verify(notification, never()).sendMessageToUser(anyLong(), anyString());
        verify(school, never()).dueReminderEvents(any(), any());
    }

    @Test
    void sendsAtTheSendHourAndLaterTheSameDayCatchUp() {
        oneSchool("Asia/Tehran");
        dueToday();
        members(member(1, "PARENT", true));

        job().runOnce(Instant.parse("2026-10-06T17:00:00Z")); // ۲۰:۳۰ تهران

        verify(notification).sendMessageToUser(eq(1L), anyString());
    }

    @Test
    void aSchoolInAnotherTimezoneUsesItsOwnLocalHourAndDate() {
        oneSchool("Europe/Berlin"); // 05:00Z = 07:00 برلین (CEST): هنوز زود است
        when(school.dueReminderEvents(new SchoolId(SCHOOL), TODAY))
                .thenReturn(List.of(new ReminderEvent(EVENT, TODAY, "جشن", 0)));
        members(member(1, "PARENT", true));

        job().runOnce(TEHRAN_0830);
        verify(notification, never()).sendMessageToUser(anyLong(), anyString());

        job().runOnce(Instant.parse("2026-10-06T06:00:00Z")); // 08:00 برلین
        verify(notification).sendMessageToUser(eq(1L), anyString());
    }

    @Test
    void theLocalDateComesFromTheSchoolTimezone() {
        oneSchool("Pacific/Auckland"); // NZDT +13: 2026-10-06T20:00Z = ۷ اکتبر ساعت ۰۹:۰۰
        LocalDate nzDay = LocalDate.of(2026, 10, 7);
        when(school.dueReminderEvents(new SchoolId(SCHOOL), nzDay))
                .thenReturn(List.of(new ReminderEvent(EVENT, nzDay, "جشن", 0)));
        members(member(1, "PARENT", true));

        job().runOnce(Instant.parse("2026-10-06T20:00:00Z"));

        verify(school).dueReminderEvents(new SchoolId(SCHOOL), nzDay);
        verify(notification).sendMessageToUser(eq(1L), anyString());
    }

    @Test
    void anInvalidOrBlankTimezoneFallsBackToTehran() {
        for (String tz : new String[]{"Not/AZone", "", null}) {
            claimed.clear();
            oneSchool(tz);
            dueToday();
            members(member(1, "PARENT", true));

            job().runOnce(TEHRAN_0830);
        }
        verify(notification, org.mockito.Mockito.times(3)).sendMessageToUser(eq(1L), anyString());
        verify(school, never()).dueReminderEvents(any(), eq(LocalDate.of(2026, 10, 7)));
    }

    @Test
    void aSuspendedSchoolIsSkipped() {
        when(school.listSchools()).thenReturn(List.of(info(SCHOOL, SchoolStatus.SUSPENDED, "Asia/Tehran")));
        dueToday();
        members(member(1, "PARENT", true));

        job().runOnce(TEHRAN_0830);

        verify(school, never()).dueReminderEvents(any(), any());
        verify(notification, never()).sendMessageToUser(anyLong(), anyString());
    }

    @Test
    void noDueEventsMeansNoSend() {
        oneSchool("Asia/Tehran");
        when(school.dueReminderEvents(any(), any())).thenReturn(List.of());
        members(member(1, "PARENT", true));

        job().runOnce(TEHRAN_0830);

        verify(notification, never()).sendMessageToUser(anyLong(), anyString());
        assertThat(claimed).isEmpty();
    }

    @Test
    void whenDisabledTheJobDoesNothing() {
        oneSchool("Asia/Tehran");

        new EventReminderJob(school, iam, notification, ledger, false, 8).runOnce(TEHRAN_0830);

        verify(school, never()).listSchools();
    }

    // ---- گیرنده‌ها ----

    @Test
    void selectedRolesOnlyAndInactiveMembersNever() {
        oneSchool("Asia/Tehran");
        dueToday();
        when(school.reminderRoleCodes(new SchoolId(SCHOOL))).thenReturn(Optional.of(Set.of("PARENT", "STUDENT")));
        members(member(1, "PARENT", true), member(2, "TEACHER", true), member(3, "STUDENT", true),
                member(4, "PARENT", false));

        job().runOnce(TEHRAN_0830);

        verify(notification).sendMessageToUser(eq(1L), anyString());
        verify(notification).sendMessageToUser(eq(3L), anyString());
        verify(notification, never()).sendMessageToUser(eq(2L), anyString());
        verify(notification, never()).sendMessageToUser(eq(4L), anyString());
    }

    @Test
    void noRoleSelectionMeansEveryActiveMember() {
        oneSchool("Asia/Tehran");
        dueToday();
        members(member(1, "PARENT", true), member(2, "TEACHER", true), member(4, "PARENT", false));

        job().runOnce(TEHRAN_0830);

        verify(notification).sendMessageToUser(eq(1L), anyString());
        verify(notification).sendMessageToUser(eq(2L), anyString());
        verify(notification, never()).sendMessageToUser(eq(4L), anyString());
    }

    @Test
    void severalMembershipsOfOneUserGetOneMessage() {
        oneSchool("Asia/Tehran");
        dueToday();
        members(member(1, "PARENT", true), member(1, "TEACHER", true));

        job().runOnce(TEHRAN_0830);

        verify(notification, org.mockito.Mockito.times(1)).sendMessageToUser(eq(1L), anyString());
    }

    // ---- دفتر ----

    @Test
    void theLedgerPreventsASecondSendOnASecondRun() {
        oneSchool("Asia/Tehran");
        dueToday();
        members(member(1, "PARENT", true));

        EventReminderJob job = job();
        job.runOnce(TEHRAN_0830);
        job.runOnce(Instant.parse("2026-10-06T06:00:00Z"));

        verify(notification, org.mockito.Mockito.times(1)).sendMessageToUser(eq(1L), anyString());
    }

    @Test
    void aRecipientIsNotRetriedAfterAFalseSend() {
        oneSchool("Asia/Tehran");
        dueToday();
        members(member(1, "PARENT", true));
        when(notification.sendMessageToUser(anyLong(), anyString())).thenReturn(false);

        EventReminderJob job = job();
        job.runOnce(TEHRAN_0830);
        job.runOnce(Instant.parse("2026-10-06T06:00:00Z"));

        verify(notification, org.mockito.Mockito.times(1)).sendMessageToUser(eq(1L), anyString());
    }

    // ---- ایزوله‌سازی ----

    @Test
    void oneFailingSendDoesNotStopTheOthers() {
        oneSchool("Asia/Tehran");
        dueToday();
        members(member(1, "PARENT", true), member(2, "PARENT", true), member(3, "PARENT", true));
        when(notification.sendMessageToUser(eq(2L), anyString())).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> job().runOnce(TEHRAN_0830)).doesNotThrowAnyException();

        verify(notification).sendMessageToUser(eq(1L), anyString());
        verify(notification).sendMessageToUser(eq(3L), anyString());
    }

    @Test
    void oneFailingSchoolDoesNotStopTheOthers() {
        when(school.listSchools()).thenReturn(List.of(
                info(SCHOOL, SchoolStatus.ACTIVE, "Asia/Tehran"), info(6L, SchoolStatus.ACTIVE, "Asia/Tehran")));
        when(school.dueReminderEvents(new SchoolId(SCHOOL), TODAY)).thenThrow(new IllegalStateException("boom"));
        when(school.dueReminderEvents(new SchoolId(6L), TODAY))
                .thenReturn(List.of(new ReminderEvent(EVENT, TODAY, "جشن", 0)));
        when(iam.findMembershipsBySchool(6L)).thenReturn(List.of(
                new MembershipInfo(1L, new UserId(9L), "u@x.test", "نام", 6L, "PARENT", true, true)));

        assertThatCode(() -> job().runOnce(TEHRAN_0830)).doesNotThrowAnyException();

        verify(notification).sendMessageToUser(eq(9L), anyString());
    }

    @Test
    void aFailureListingSchoolsNeverEscapes() {
        when(school.listSchools()).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> job().runOnce(TEHRAN_0830)).doesNotThrowAnyException();
    }

    @Test
    void tenantContextIsClearedAfterARunAndAfterAnException() {
        oneSchool("Asia/Tehran");
        dueToday();
        members(member(1, "PARENT", true));
        job().runOnce(TEHRAN_0830);
        assertThat(TenantContext.isSet()).isFalse();

        when(school.dueReminderEvents(any(), any())).thenThrow(new IllegalStateException("boom"));
        job().runOnce(TEHRAN_0830);
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void tenantContextIsSetToTheSchoolWhileItsEventsAreLoaded() {
        oneSchool("Asia/Tehran");
        when(school.dueReminderEvents(any(), any())).thenAnswer(inv -> {
            assertThat(TenantContext.getOrNull()).isEqualTo(SCHOOL);
            return List.of();
        });

        job().runOnce(TEHRAN_0830);

        verify(school).dueReminderEvents(any(), any());
    }

    // ---- پیام و کانفیگ ----

    @Test
    void messageTextForTodayTomorrowAndNDays() {
        assertThat(EventReminderJob.buildMessage("جشن", 0, "۱۴ مهر ۱۴۰۵", "دبستان رشد"))
                .isEqualTo("یادآوری: رویداد «جشن» امروز (۱۴ مهر ۱۴۰۵) در دبستان رشد برگزار می‌شود.");
        assertThat(EventReminderJob.buildMessage("جشن", 1, "۱۴ مهر ۱۴۰۵", "دبستان رشد"))
                .isEqualTo("یادآوری: رویداد «جشن» فردا (۱۴ مهر ۱۴۰۵) در دبستان رشد برگزار می‌شود.");
        assertThat(EventReminderJob.buildMessage("جشن", 12, "۱۴ مهر ۱۴۰۵", "دبستان رشد"))
                .isEqualTo("یادآوری: رویداد «جشن» ۱۲ روز دیگر (۱۴ مهر ۱۴۰۵) در دبستان رشد برگزار می‌شود.");
    }

    @Test
    void theSentTextUsesTheDaysLeftAndTheSchoolName() {
        oneSchool("Asia/Tehran");
        LocalDate day = TODAY.plusDays(3);
        when(school.dueReminderEvents(new SchoolId(SCHOOL), TODAY))
                .thenReturn(List.of(new ReminderEvent(EVENT, day, "جشن", 3)));
        members(member(1, "PARENT", true));

        job().runOnce(TEHRAN_0830);

        verify(notification).sendMessageToUser(1L,
                "یادآوری: رویداد «جشن» ۳ روز دیگر (۱۴ مهر ۱۴۰۵) در دبستان 5 برگزار می‌شود.");
    }

    @Test
    void sendHourMustBeZeroToTwentyThree() {
        assertThatCode(() -> EventReminderJob.validateSendHour(0)).doesNotThrowAnyException();
        assertThatCode(() -> EventReminderJob.validateSendHour(23)).doesNotThrowAnyException();
        assertThatThrownBy(() -> EventReminderJob.validateSendHour(24)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EventReminderJob.validateSendHour(-1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new EventReminderJob(school, iam, notification, ledger, true, 25))
                .isInstanceOf(IllegalStateException.class);
    }
}
