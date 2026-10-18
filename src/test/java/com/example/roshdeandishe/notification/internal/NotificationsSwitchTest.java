package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.AcademicFacade.ClassId;
import com.example.roshdeandishe.academic.AcademicFacade.StudentId;
import com.example.roshdeandishe.academic.AcademicFacade.StudentInfo;
import com.example.roshdeandishe.attendance.AbsenceRecordedEvent;
import com.example.roshdeandishe.finance.InvoiceIssuedEvent;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.iam.IamFacade.UserProfile;
import com.example.roshdeandishe.notification.NotificationFacade;
import com.example.roshdeandishe.notification.NotificationFacade.DeliveryResult;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * سوئیچِ «اعلان‌ها» (school-settings، تصمیم مالک ۲۰۲۶-۱۰-۱۸): با خاموش‌بودنِ آن برای یک مدرسه، سه فرستنده‌ی خودکار
 * (غیبت، فاکتور جدید، یادآوری رویداد) به کاربرانِ آن مدرسه چیزی نمی‌فرستند و آن را «تلاش دوباره» حساب نمی‌کنند؛
 * همان کارها برای مدرسه‌ی دیگر عادی کار می‌کنند. پیام‌های بدونِ مدرسه (مسیر پاسخ و لینک‌کردن) هیچ‌وقت بسته نمی‌شوند.
 * بدون Spring: NotificationFacadeImpl واقعی، کانالِ fake و SchoolFacade ساختگی.
 */
class NotificationsSwitchTest {

    private static final long OFF = 5L;
    private static final long ON = 6L;
    private static final long STUDENT_OFF = 11L;
    private static final long STUDENT_ON = 12L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);
    private static final Instant TEHRAN_0830 = Instant.parse("2026-10-06T05:00:00Z");

    /** (کاربر، مدرسه) هر پیامی که کانال رسانده. */
    private final List<String> delivered = new ArrayList<>();
    private int channelCalls;
    private SchoolFacade school;
    private AcademicFacade academic;
    private IamFacade iam;
    private NotificationFacadeImpl notifications;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        school = mock(SchoolFacade.class);
        academic = mock(AcademicFacade.class);
        iam = mock(IamFacade.class);
        when(school.isNotificationsEnabled(new SchoolId(OFF))).thenReturn(false);
        when(school.isNotificationsEnabled(new SchoolId(ON))).thenReturn(true);
        when(school.formatSolarDate(any())).thenReturn("۱۴ مهر ۱۴۰۵");
        when(school.reminderRoleCodes(any())).thenReturn(Optional.empty());
        for (long id : new long[]{OFF, ON}) {
            when(school.findSchool(new SchoolId(id))).thenReturn(Optional.of(info(id)));
        }
        NotificationChannel channel = new NotificationChannel() {
            @Override
            public boolean send(Long userId, String text) {
                return send(userId, null, text);
            }

            private boolean send(Long userId, Long schoolId, String text) {
                channelCalls++;
                delivered.add(userId + "@" + schoolId);
                return true;
            }

            @Override
            public DeliveryResult deliver(Long userId, Long schoolId, String text) {
                return send(userId, schoolId, text) ? DeliveryResult.SENT : DeliveryResult.UNDELIVERABLE;
            }
        };
        notifications = new NotificationFacadeImpl(List.of(channel), null, null, school);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static SchoolInfo info(long id) {
        return new SchoolInfo(new SchoolId(id), "دبستان " + id, SchoolStatus.ACTIVE, "Asia/Tehran", "fa-IR",
                SchoolLevel.ELEMENTARY, null, WeekStart.SATURDAY);
    }

    private void student(long schoolId, long studentId) {
        when(academic.findStudentInfo(studentId)).thenReturn(Optional.of(
                new StudentInfo(new StudentId(studentId), 1000 + studentId, schoolId, new ClassId(7L))));
        when(academic.listGuardians(schoolId, studentId)).thenReturn(List.of(500 + studentId));
        when(iam.findUser(new UserId(1000 + studentId))).thenReturn(Optional.of(
                new UserProfile(new UserId(1000 + studentId), "s@x.test", "علی رضایی", null)));
    }

    // ---- خودِ Facade ----

    @Test
    void aSchoolWithNotificationsOffGetsTheNonSendingResultAndTheChannelIsNeverCalled() {
        assertThat(notifications.deliverMessageToUser(1L, OFF, "سلام")).isEqualTo(DeliveryResult.UNDELIVERABLE);
        assertThat(notifications.sendMessageToUser(1L, OFF, "سلام")).isFalse();
        assertThat(channelCalls).isZero();
    }

    @Test
    void anotherSchoolAndSchoollessMessagesAreNotGated() {
        assertThat(notifications.deliverMessageToUser(1L, ON, "سلام")).isEqualTo(DeliveryResult.SENT);
        // بدونِ مدرسه (مسیر مشترک: لینک‌کردن، پاسخ) بسته نمی‌شود
        assertThat(notifications.deliverMessageToUser(2L, "سلام")).isEqualTo(DeliveryResult.SENT);
        assertThat(notifications.sendMessageToUser(3L, "سلام")).isTrue();
        assertThat(delivered).containsExactly("1@6", "2@null", "3@null");
    }

    // ---- سه فرستنده ----

    @Test
    void absenceNoticeIsNotSentForTheSchoolWithNotificationsOffButIsForTheOther() {
        student(OFF, STUDENT_OFF);
        student(ON, STUDENT_ON);
        AbsenceRecordedEventListener listener = new AbsenceRecordedEventListener(academic, iam, school, notifications);

        listener.onAbsenceRecorded(new AbsenceRecordedEvent(OFF, 7L, STUDENT_OFF, TODAY, 90L, Instant.EPOCH));
        assertThat(delivered).isEmpty();

        listener.onAbsenceRecorded(new AbsenceRecordedEvent(ON, 7L, STUDENT_ON, TODAY, 90L, Instant.EPOCH));
        assertThat(delivered).containsExactly("512@6");
    }

    @Test
    void invoiceNoticeIsNotSentForTheSchoolWithNotificationsOffButIsForTheOther() {
        student(OFF, STUDENT_OFF);
        student(ON, STUDENT_ON);
        InvoiceIssuedEventListener listener = new InvoiceIssuedEventListener(academic, notifications);

        listener.onInvoiceIssued(new InvoiceIssuedEvent(OFF, STUDENT_OFF, BigDecimal.TEN, null, Instant.EPOCH));
        assertThat(delivered).isEmpty();

        listener.onInvoiceIssued(new InvoiceIssuedEvent(ON, STUDENT_ON, BigDecimal.TEN, null, Instant.EPOCH));
        assertThat(delivered).containsExactlyInAnyOrder("1012@6", "512@6");
    }

    @Test
    void eventReminderIsNotSentForTheSchoolWithNotificationsOffAndIsNotRetriedEveryHour() {
        when(school.listSchools()).thenReturn(List.of(info(OFF), info(ON)));
        for (long id : new long[]{OFF, ON}) {
            when(school.dueReminderEvents(new SchoolId(id), TODAY))
                    .thenReturn(List.of(new ReminderEvent(100 + id, TODAY, "جشن", 0)));
            when(iam.findMembershipsBySchool(id)).thenReturn(List.of(
                    new MembershipInfo(id, new UserId(id * 10), "u@x.test", "نام", id, "PARENT", true, true)));
        }
        Set<String> claimed = new HashSet<>();
        EventReminderSentRepository ledger = new EventReminderSentRepository() {
            @Override
            public int claim(long entryId, long userId) {
                return claimed.add(entryId + ":" + userId) ? 1 : 0;
            }

            @Override
            public int release(long entryId, long userId) {
                return claimed.remove(entryId + ":" + userId) ? 1 : 0;
            }
        };
        EventReminderJob job = new EventReminderJob(school, iam, notifications, ledger, true, 8);

        job.runOnce(TEHRAN_0830);
        assertThat(delivered).containsExactly("60@6");
        // خاموش = نتیجه‌ی قطعی: ادعا آزاد نمی‌شود، پس ساعتِ بعد دوباره تلاش نمی‌شود
        assertThat(claimed).contains("105:50", "106:60");

        job.runOnce(TEHRAN_0830.plusSeconds(3600));
        assertThat(delivered).containsExactly("60@6");
    }
}
