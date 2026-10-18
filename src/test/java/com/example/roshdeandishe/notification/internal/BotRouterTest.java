package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.iam.IamFacade.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BotRouterTest {

    private SubjectResolver resolver;
    private StudentAnswers studentAnswers;
    private TimetableAnswers timetableAnswers;
    private SchoolAnswers schoolAnswers;
    private BotRouter router;
    private final BotContext ctx = new BotContext(42L, new UserId(7L), "علی", List.of());

    @BeforeEach
    void setUp() {
        resolver = mock(SubjectResolver.class);
        studentAnswers = mock(StudentAnswers.class);
        timetableAnswers = mock(TimetableAnswers.class);
        schoolAnswers = mock(SchoolAnswers.class);
        router = new BotRouter(resolver, studentAnswers, timetableAnswers, schoolAnswers);
        when(resolver.targets(any(), any())).thenReturn(List.of());
    }

    private static BotTarget.Student student(long id, String name, String school, boolean ward) {
        return new BotTarget.Student(10L, id, 1000L + id, null, name, school, ward);
    }

    @Test
    void studentMenuHasNoInvoiceButton() {
        when(resolver.hasAny(ctx, BotIntent.TargetKind.STUDENT_TARGET)).thenReturn(true);
        when(resolver.hasAny(ctx, BotIntent.TargetKind.MEMBER_SCHOOL)).thenReturn(true);

        List<String> flat = router.menu(ctx).stream().flatMap(List::stream).toList();

        assertThat(flat).doesNotContain(BotTexts.BTN_INVOICES);
        assertThat(flat).contains(BotTexts.BTN_GRADES, BotTexts.BTN_EVENTS);
        assertThat(flat).last().isEqualTo(BotTexts.BTN_HELP);
        assertThat(router.menu(ctx)).allSatisfy(row -> assertThat(row).hasSizeLessThanOrEqualTo(2));
    }

    @Test
    void teacherMenuHasTeacherButtons() {
        when(resolver.hasAny(ctx, BotIntent.TargetKind.TEACHER_SCHOOL)).thenReturn(true);
        when(resolver.hasAny(ctx, BotIntent.TargetKind.MEMBER_SCHOOL)).thenReturn(true);

        assertThat(router.menu(ctx)).containsExactly(
                List.of(BotTexts.BTN_TEACHER_TODAY, BotTexts.BTN_TEACHER_ROLL),
                List.of(BotTexts.BTN_EVENTS, BotTexts.BTN_HELP));
    }

    @Test
    void menuChecksEachTargetKindOnce() {
        router.menu(ctx);

        verify(resolver, org.mockito.Mockito.times(4)).hasAny(any(), any());
        verify(resolver, org.mockito.Mockito.never()).targets(any(), any());
    }

    @Test
    void menuWithNoTargetsHasOnlyHelp() {
        assertThat(router.menu(ctx)).containsExactly(List.of(BotTexts.BTN_HELP));
    }

    @Test
    void helpForStudentDescribesOnlyTheirButtons() {
        when(resolver.hasAny(ctx, BotIntent.TargetKind.STUDENT_TARGET)).thenReturn(true);
        when(resolver.hasAny(ctx, BotIntent.TargetKind.MEMBER_SCHOOL)).thenReturn(true);

        String help = router.help(ctx);

        assertThat(help).startsWith(BotTexts.HELP_HEADER)
                .contains(BotTexts.helpLine(List.of(BotTexts.BTN_ATTENDANCE_TODAY, BotTexts.BTN_ATTENDANCE_SUMMARY,
                        BotTexts.BTN_GRADES, BotTexts.BTN_EXAMS, BotTexts.BTN_TIMETABLE_TODAY), BotTexts.HELP_STUDENT))
                .contains(BotTexts.helpLine(List.of(BotTexts.BTN_EVENTS), BotTexts.HELP_EVENTS))
                .contains(BotTexts.CMD_MY_SCHOOLS)
                .doesNotContain(BotTexts.BTN_INVOICES, BotTexts.BTN_TEACHER_TODAY, BotTexts.CMD_MY_WARDS);
    }

    @Test
    void helpForParentListsInvoicesAndWards() {
        BotContext parent = new BotContext(42L, new UserId(7L), "علی",
                List.of(new com.example.roshdeandishe.iam.IamFacade.MembershipInfo(1L, new UserId(7L), "a@b.c", "علی", 10L, "PARENT", true, true)));
        when(resolver.hasAny(parent, BotIntent.TargetKind.STUDENT_TARGET)).thenReturn(true);
        when(resolver.hasAny(parent, BotIntent.TargetKind.WARD_TARGET)).thenReturn(true);

        assertThat(router.help(parent))
                .contains(BotTexts.helpLine(List.of(BotTexts.BTN_INVOICES), BotTexts.HELP_INVOICES))
                .contains(BotTexts.helpLine(List.of(BotTexts.CMD_MY_WARDS), BotTexts.HELP_MY_WARDS));
    }

    @Test
    void helpWithNothingAvailableSaysSo() {
        assertThat(router.help(ctx)).startsWith(BotTexts.HELP_NOTHING_AVAILABLE)
                .doesNotContain(BotTexts.HELP_HEADER);
    }

    @Test
    void helpLineJoinsLabelsInPersian() {
        assertThat(BotTexts.helpLine(List.of("الف", "ب", "ج"), "توضیح")).isEqualTo("«الف»، «ب» و «ج» - توضیح");
        assertThat(BotTexts.helpLine(List.of("الف"), "توضیح")).isEqualTo("«الف» - توضیح");
    }

    @Test
    void singleTargetAnswersDirectly() {
        BotTarget own = student(100L, "علی", "مدرسه", false);
        when(resolver.targets(ctx, BotIntent.GRADES)).thenReturn(List.of(own));
        when(studentAnswers.answer(BotIntent.GRADES, ctx, own)).thenReturn("نمره‌ها");

        BotRouter.Reply reply = router.onText(ctx, BotTexts.BTN_GRADES);

        assertThat(reply.text()).isEqualTo("نمره‌ها");
        assertThat(reply.choices()).isEmpty();
    }

    @Test
    void severalTargetsAskWithButtons() {
        BotTarget a = student(100L, "سارا", "مدرسه‌ی ۱", true);
        BotTarget b = student(200L, "رضا", "مدرسه‌ی ۲", true);
        when(resolver.targets(ctx, BotIntent.GRADES)).thenReturn(List.of(a, b));

        BotRouter.Reply reply = router.onText(ctx, BotTexts.BTN_GRADES);

        assertThat(reply.text()).isEqualTo(BotTexts.CHOOSE_STUDENT);
        assertThat(reply.choices()).hasSize(2);
        assertThat(reply.choices().get(0)).containsExactly(
                new com.example.roshdeandishe.notification.internal.BaleClient.InlineButton(
                        BotTexts.studentChoice("سارا", "مدرسه‌ی ۱"), CallbackCodec.encode(BotIntent.GRADES, a)));
        assertThat(reply.choices().get(1).get(0).callbackData()).isEqualTo("g:200");
        verifyNoInteractions(studentAnswers);
    }

    @Test
    void severalSchoolsAskWhichSchool() {
        BotTarget a = new BotTarget.School(10L, "الف");
        BotTarget b = new BotTarget.School(20L, "ب");
        when(resolver.targets(ctx, BotIntent.EVENTS)).thenReturn(List.of(a, b));

        BotRouter.Reply reply = router.onText(ctx, BotTexts.BTN_EVENTS);

        assertThat(reply.text()).isEqualTo(BotTexts.CHOOSE_SCHOOL);
        assertThat(reply.choices().get(0).get(0).text()).isEqualTo("الف");
        assertThat(reply.choices().get(1).get(0).callbackData()).isEqualTo("ev:20");
    }

    @Test
    void zeroTargetsIsNotAvailable() {
        assertThat(router.onText(ctx, BotTexts.BTN_INVOICES).text()).isEqualTo(BotTexts.NOT_AVAILABLE);
    }

    @Test
    void unknownTextIsNull() {
        assertThat(router.onText(ctx, "چیز دیگر")).isNull();
    }

    @Test
    void callbackForOwnTargetAnswers() {
        BotTarget a = student(123L, "سارا", "مدرسه", true);
        when(resolver.targets(ctx, BotIntent.GRADES)).thenReturn(List.of(a));
        when(studentAnswers.answer(BotIntent.GRADES, ctx, a)).thenReturn("پاسخ");

        assertThat(router.onCallback(ctx, "g:123").text()).isEqualTo("پاسخ");
    }

    @Test
    void callbackForAForeignStudentIsNotAvailable() {
        when(resolver.targets(ctx, BotIntent.GRADES)).thenReturn(List.of(student(123L, "سارا", "مدرسه", true)));

        assertThat(router.onCallback(ctx, "g:999").text()).isEqualTo(BotTexts.NOT_AVAILABLE);
        verifyNoInteractions(studentAnswers);
    }

    @Test
    void malformedCallbackIsNotAvailable() {
        for (String data : new String[]{"X", "g:", ":1", "g:abc", "zz:1", "g:-1", ""}) {
            assertThat(router.onCallback(ctx, data).text()).isEqualTo(BotTexts.NOT_AVAILABLE);
        }
        assertThat(router.onCallback(ctx, null).text()).isEqualTo(BotTexts.NOT_AVAILABLE);
        verifyNoInteractions(studentAnswers, timetableAnswers, schoolAnswers);
    }

    @Test
    void invoiceCallbackForOwnEnrolmentIsNotAvailable() {
        // INVOICES فقط فرزندان را برمی‌گرداند؛ ثبت‌نامِ خودِ دانش‌آموز در فهرست نیست
        when(resolver.targets(ctx, BotIntent.INVOICES)).thenReturn(List.of());

        assertThat(router.onCallback(ctx, "i:100").text()).isEqualTo(BotTexts.NOT_AVAILABLE);
        verifyNoInteractions(schoolAnswers);
    }

    @Test
    void answerFailureIsReportedAsNotAvailable() {
        BotTarget own = student(100L, "علی", "مدرسه", false);
        when(resolver.targets(ctx, BotIntent.GRADES)).thenReturn(List.of(own));
        when(studentAnswers.answer(BotIntent.GRADES, ctx, own)).thenThrow(new IllegalStateException("secret detail"));

        assertThat(router.onText(ctx, BotTexts.BTN_GRADES).text()).isEqualTo(BotTexts.NOT_AVAILABLE);
    }

    @Test
    void routesIntentsToTheRightAnswerClass() {
        BotTarget school = new BotTarget.School(10L, "مدرسه");
        when(resolver.targets(ctx, BotIntent.TEACHER_TODAY)).thenReturn(List.of(school));
        when(resolver.targets(ctx, BotIntent.TEACHER_ROLL)).thenReturn(List.of(school));
        when(timetableAnswers.answer(BotIntent.TEACHER_TODAY, ctx, school)).thenReturn("tt");
        when(schoolAnswers.answer(BotIntent.TEACHER_ROLL, ctx, school)).thenReturn("tr");

        assertThat(router.onText(ctx, BotTexts.BTN_TEACHER_TODAY).text()).isEqualTo("tt");
        assertThat(router.onText(ctx, BotTexts.BTN_TEACHER_ROLL).text()).isEqualTo("tr");
        verify(timetableAnswers).answer(BotIntent.TEACHER_TODAY, ctx, school);
    }

    @Test
    void resolveWithStudentIdAnswersThatTarget() {
        BotTarget a = student(100L, "سارا", "مدرسه", true);
        BotTarget b = student(200L, "رضا", "مدرسه", true);
        when(resolver.targets(ctx, BotIntent.GRADES)).thenReturn(List.of(a, b));
        when(studentAnswers.answer(BotIntent.GRADES, ctx, b)).thenReturn("نمره‌های رضا");

        BotRouter.Reply reply = router.resolve(ctx, BotIntent.GRADES, 200L);

        assertThat(reply.text()).isEqualTo("نمره‌های رضا");
        assertThat(reply.choices()).isEmpty();
    }

    @Test
    void resolveWithoutIdAndTwoTargetsAsksToChoose() {
        BotTarget a = student(100L, "سارا", "مدرسه", true);
        BotTarget b = student(200L, "رضا", "مدرسه", true);
        when(resolver.targets(ctx, BotIntent.GRADES)).thenReturn(List.of(a, b));

        BotRouter.Reply reply = router.resolve(ctx, BotIntent.GRADES, null);

        assertThat(reply.text()).isEqualTo(BotTexts.CHOOSE_STUDENT);
        assertThat(reply.choices()).hasSize(2);
        verifyNoInteractions(studentAnswers);
    }

    @Test
    void resolveWithoutIdAndOneTargetAnswersDirectly() {
        BotTarget a = student(100L, "سارا", "مدرسه", true);
        when(resolver.targets(ctx, BotIntent.GRADES)).thenReturn(List.of(a));
        when(studentAnswers.answer(BotIntent.GRADES, ctx, a)).thenReturn("نمره‌ها");

        assertThat(router.resolve(ctx, BotIntent.GRADES, null).text()).isEqualTo("نمره‌ها");
    }

    @Test
    void resolveWithForeignIdIsNotAvailable() {
        BotTarget a = student(100L, "سارا", "مدرسه", true);
        when(resolver.targets(ctx, BotIntent.GRADES)).thenReturn(List.of(a));

        BotRouter.Reply reply = router.resolve(ctx, BotIntent.GRADES, 999L);

        assertThat(reply.text()).isEqualTo(BotTexts.NOT_AVAILABLE);
        verifyNoInteractions(studentAnswers);
    }

    @Test
    void resolveWithStudentIdNeverMatchesASchoolId() {
        BotTarget school = new BotTarget.School(555L, "مدرسه");
        when(resolver.targets(ctx, BotIntent.EVENTS)).thenReturn(List.of(school));

        assertThat(router.resolve(ctx, BotIntent.EVENTS, 555L).text()).isEqualTo(BotTexts.NOT_AVAILABLE);
        verifyNoInteractions(schoolAnswers);
    }

    @Test
    void resolveWithNoTargetsIsNotAvailable() {
        assertThat(router.resolve(ctx, BotIntent.GRADES, null).text()).isEqualTo(BotTexts.NOT_AVAILABLE);
    }

    @Test
    void allowedIntentsFollowTheMenu() {
        when(resolver.hasAny(ctx, BotIntent.TargetKind.STUDENT_TARGET)).thenReturn(true);

        assertThat(router.allowedIntents(ctx)).containsExactly(BotIntent.ATTENDANCE_TODAY,
                BotIntent.ATTENDANCE_SUMMARY, BotIntent.GRADES, BotIntent.EXAMS, BotIntent.TIMETABLE_TODAY);
    }
}
