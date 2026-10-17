package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.studentview.StudentSummary;
import com.example.roshdeandishe.studentview.StudentSummary.AttendanceCount;
import com.example.roshdeandishe.studentview.StudentSummary.ExamLine;
import com.example.roshdeandishe.studentview.StudentSummary.GradeLine;
import com.example.roshdeandishe.studentview.StudentViewFacade;
import com.example.roshdeandishe.studentview.TodayAttendance;
import com.example.roshdeandishe.attendance.shared.AttendanceError;
import com.example.roshdeandishe.attendance.shared.AttendanceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudentAnswersTest {

    private static final UserId USER = new UserId(7L);

    private StudentViewFacade facade;
    private StudentAnswers answers;
    private final BotContext ctx = new BotContext(1L, USER, "والد", List.of());
    private final BotTarget.Student ward = new BotTarget.Student(5L, 11L, 70L, 3L, "علی رضایی", "مدرسه‌ی نمونه", true);
    private final BotTarget.Student own = new BotTarget.Student(5L, 11L, 7L, 3L, "علی رضایی", "مدرسه‌ی نمونه", false);

    @BeforeEach
    void setUp() {
        facade = mock(StudentViewFacade.class);
        answers = new StudentAnswers(facade);
    }

    private void today(TodayAttendance state) {
        when(facade.todayAttendance(USER, 5L, 3L, 11L)).thenReturn(state);
    }

    @Test
    void firstLineNamesStudentAndSchool() {
        today(TodayAttendance.PRESENT);
        assertThat(answers.answer(BotIntent.ATTENDANCE_TODAY, ctx, ward))
                .startsWith("«علی رضایی — مدرسه‌ی نمونه»\n").contains("امروز حاضر بود.");
    }

    @Test
    void pendingRollIsNeverShownAsPresent() {
        today(TodayAttendance.PENDING);
        assertThat(answers.answer(BotIntent.ATTENDANCE_TODAY, ctx, ward))
                .contains("هنوز ثبت نشده").doesNotContain("حاضر");
    }

    @Test
    void ownEnrolmentIsAddressedInFirstPerson() {
        today(TodayAttendance.PRESENT);
        assertThat(answers.answer(BotIntent.ATTENDANCE_TODAY, ctx, own)).contains("امروز حاضر بودی.");
        today(TodayAttendance.ABSENT_EXCUSED);
        assertThat(answers.answer(BotIntent.ATTENDANCE_TODAY, ctx, own)).contains("امروز غایب بودی (موجه).");
        today(TodayAttendance.LATE);
        assertThat(answers.answer(BotIntent.ATTENDANCE_TODAY, ctx, ward)).contains("امروز با تأخیر آمد.");
    }

    @Test
    void everyStateHasItsSentence() {
        today(TodayAttendance.ABSENT);
        assertThat(answers.answer(BotIntent.ATTENDANCE_TODAY, ctx, ward)).contains("امروز غایب بود.");
        today(TodayAttendance.NOT_SCHOOL_DAY);
        assertThat(answers.answer(BotIntent.ATTENDANCE_TODAY, ctx, ward)).contains("امروز روز درسی نیست.");
        today(TodayAttendance.UNAVAILABLE);
        assertThat(answers.answer(BotIntent.ATTENDANCE_TODAY, ctx, ward))
                .contains("اطلاعات حضور و غیاب در دسترس نیست.");
    }

    @Test
    void wardWithoutClassIsUnavailableNotACrash() {
        BotTarget.Student noClass = new BotTarget.Student(5L, 11L, 70L, null, "علی رضایی", "مدرسه‌ی نمونه", true);
        when(facade.todayAttendance(USER, 5L, null, 11L)).thenReturn(TodayAttendance.UNAVAILABLE);
        assertThat(answers.answer(BotIntent.ATTENDANCE_TODAY, ctx, noClass))
                .contains("در دسترس نیست");
    }

    @Test
    void summaryShowsCounts() {
        when(facade.summary(USER, 5L, 11L)).thenReturn(
                new StudentSummary(List.of(), List.of(), new AttendanceCount(2, 1, 3)));
        assertThat(answers.answer(BotIntent.ATTENDANCE_SUMMARY, ctx, ward)).contains(
                "غیبت بدون عذر: ۲ — موجه: ۱ — تأخیر: ۳ (امسال، فقط روزهای ثبت‌شده)");
    }

    @Test
    void nullAttendanceIsUnavailable() {
        when(facade.summary(USER, 5L, 11L)).thenReturn(new StudentSummary(List.of(), List.of(), null));
        assertThat(answers.answer(BotIntent.ATTENDANCE_SUMMARY, ctx, ward))
                .contains("اطلاعات حضور و غیاب در دسترس نیست.");
    }

    @Test
    void gradesListLatestWithoutAverage() {
        when(facade.summary(USER, 5L, 11L)).thenReturn(new StudentSummary(
                List.of(new GradeLine("ریاضی", "نوبت اول", "18.5"), new GradeLine("علوم", "نوبت دوم", "۲۰")),
                List.of(), null));
        String text = answers.answer(BotIntent.GRADES, ctx, ward);
        assertThat(text).contains("ریاضی — نوبت اول: ۱۸٫۵").contains("علوم — نوبت دوم: ۲۰")
                .doesNotContain("میانگین");
        assertThat(text.lines().count()).isEqualTo(3);
    }

    @Test
    void noGradesSaysSo() {
        when(facade.summary(USER, 5L, 11L)).thenReturn(new StudentSummary(List.of(), List.of(), null));
        assertThat(answers.answer(BotIntent.GRADES, ctx, ward)).contains("هنوز نمره‌ای ثبت نشده است.");
    }

    @Test
    void examsAreListedOrEmptySentence() {
        when(facade.summary(USER, 5L, 11L)).thenReturn(new StudentSummary(
                List.of(), List.of(new ExamLine("آزمون هفتگی", "۱۴ مهر ۱۴۰۵")), null));
        assertThat(answers.answer(BotIntent.EXAMS, ctx, ward)).contains("آزمون هفتگی — ۱۴ مهر ۱۴۰۵");
        when(facade.summary(USER, 5L, 11L)).thenReturn(new StudentSummary(List.of(), List.of(), null));
        assertThat(answers.answer(BotIntent.EXAMS, ctx, ward)).contains("امتحان پیش‌رویی ندارد.");
    }

    @Test
    void facadeErrorBecomesNotAvailableSentence() {
        when(facade.summary(any(), any(), any())).thenThrow(
                new AttendanceException(new AttendanceError.SchoolNotFound(5L)));
        assertThat(answers.answer(BotIntent.GRADES, ctx, ward)).contains("نمره‌ها در دسترس نیست.");
        assertThat(answers.answer(BotIntent.EXAMS, ctx, ward)).contains("امتحان‌ها در دسترس نیست.");
        when(facade.todayAttendance(any(), any(), any(), any())).thenThrow(
                new AttendanceException(new AttendanceError.SchoolNotFound(5L)));
        assertThat(answers.answer(BotIntent.ATTENDANCE_TODAY, ctx, ward))
                .contains("اطلاعات حضور و غیاب در دسترس نیست.");
    }
}
