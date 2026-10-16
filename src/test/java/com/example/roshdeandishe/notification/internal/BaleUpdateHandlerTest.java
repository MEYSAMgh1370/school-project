package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.AcademicFacade.StudentId;
import com.example.roshdeandishe.academic.AcademicFacade.StudentInfo;
import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.iam.IamFacade.UserProfile;
import com.example.roshdeandishe.notification.internal.BaleUpdate.BaleCallbackQuery;
import com.example.roshdeandishe.notification.internal.BaleUpdate.BaleChat;
import com.example.roshdeandishe.notification.internal.BaleUpdate.BaleMessage;
import com.example.roshdeandishe.school.SchoolFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BaleUpdateHandlerTest {

    private static final String NOT_LINKED = "حساب شما هنوز متصل نشده است. لطفاً ابتدا /start را ارسال کنید.";

    private BaleLinkRepository links;
    private BaleClient client;
    private IamFacade iam;
    private SchoolFacade school;
    private AcademicFacade academic;
    private FinanceFacade finance;
    private StudentAnswers studentAnswers;
    private SchoolAnswers schoolAnswers;
    private BaleUpdateHandler handler;

    @BeforeEach
    void setUp() {
        links = mock(BaleLinkRepository.class);
        client = mock(BaleClient.class);
        iam = mock(IamFacade.class);
        school = mock(SchoolFacade.class);
        academic = mock(AcademicFacade.class);
        finance = mock(FinanceFacade.class);
        SubjectResolver resolver = new SubjectResolver(academic, school, iam);
        studentAnswers = mock(StudentAnswers.class);
        schoolAnswers = mock(SchoolAnswers.class);
        BotRouter router = new BotRouter(resolver, studentAnswers, mock(TimetableAnswers.class), schoolAnswers);
        handler = new BaleUpdateHandler(links, client, iam, school, finance,
                new BotContextLoader(links, iam), resolver, router);
        when(academic.listWards(any(), any())).thenReturn(List.of());
        when(academic.findMyEnrollment(any(), any())).thenReturn(Optional.empty());
    }

    private static BaleUpdate callbackUpdate(String id, Long chatId, String data) {
        BaleMessage message = new BaleMessage(1L, new BaleChat(chatId), null, null);
        return new BaleUpdate(1L, null, new BaleCallbackQuery(id, message, data));
    }

    private static BaleUpdate textUpdate(Long chatId, String text) {
        return new BaleUpdate(1L, new BaleMessage(1L, new BaleChat(chatId), text, null), null);
    }

    private void link(Long chatId, Long userId) {
        when(links.findByChatId(chatId)).thenReturn(Optional.of(new BaleLinkEntity(userId, chatId, Instant.now())));
        when(iam.findUser(new UserId(userId)))
                .thenReturn(Optional.of(new UserProfile(new UserId(userId), "a@b.c", "والد", "0912")));
    }

    @Test
    void callbackFromUnlinkedChatIsAnsweredAndGetsNotLinkedText() {
        when(links.findByChatId(42L)).thenReturn(Optional.empty());

        handler.handle(callbackUpdate("cb1", 42L, "X"));

        verify(client).answerCallbackQuery("cb1");
        verify(client).sendMessage(42L, NOT_LINKED);
    }

    @Test
    void malformedCallbackFromLinkedChatIsAnsweredAndNotAvailable() {
        link(42L, 7L);
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of());

        handler.handle(callbackUpdate("cb2", 42L, "X"));

        verify(client).answerCallbackQuery("cb2");
        verify(client).sendMessage(42L, BotTexts.NOT_AVAILABLE);
    }

    @Test
    void callbackForAForeignStudentIsNotAvailableAndCallsNoAnswerClass() {
        link(42L, 7L);
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of(
                new MembershipInfo(1L, new UserId(7L), "a@b.c", "والد", 10L, "PARENT", true, true)));
        when(academic.listWards(7L, 10L)).thenReturn(List.of(
                new StudentInfo(new StudentId(100L), 1000L, 10L, null)));
        when(school.findSchool(any())).thenReturn(Optional.empty());

        handler.handle(callbackUpdate("cb3", 42L, "g:999"));

        verify(client).sendMessage(42L, BotTexts.NOT_AVAILABLE);
        verifyNoInteractions(studentAnswers);
    }

    @Test
    void unknownTextShowsTheMenu() {
        link(42L, 7L);
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of());

        handler.handle(textUpdate(42L, "سلام"));

        verify(client).sendMenu(42L, BotTexts.UNKNOWN_TEXT, List.of(List.of(BotTexts.BTN_HELP)));
    }

    @Test
    void helpAlsoSendsTheMenu() {
        link(42L, 7L);
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of());

        handler.handle(textUpdate(42L, "راهنما"));

        verify(client).sendMenu(42L, BotTexts.HELP_TEXT, List.of(List.of(BotTexts.BTN_HELP)));
    }

    @Test
    void linkingSendsTheMenu() {
        when(iam.findUserByPhoneNumber("0912"))
                .thenReturn(Optional.of(new UserProfile(new UserId(7L), "a@b.c", "والد", "0912")));
        when(links.findByUserId(7L)).thenReturn(Optional.empty());
        link(42L, 7L);
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of());

        handler.handle(new BaleUpdate(1L, new BaleMessage(1L, new BaleChat(42L), null,
                new BaleUpdate.BaleContact("0912")), null));

        verify(client).sendMenu(42L, BotTexts.linkSuccess("والد"), List.of(List.of(BotTexts.BTN_HELP)));
    }

    @Test
    void severalChoicesAreSentAsInlineButtons() {
        link(42L, 7L);
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of(
                new MembershipInfo(1L, new UserId(7L), "a@b.c", "والد", 10L, "PARENT", true, true)));
        when(academic.listWards(7L, 10L)).thenReturn(List.of(
                new StudentInfo(new StudentId(100L), 1000L, 10L, null),
                new StudentInfo(new StudentId(200L), 2000L, 10L, null)));
        when(school.findSchool(any())).thenReturn(Optional.empty());

        handler.handle(textUpdate(42L, BotTexts.BTN_GRADES));

        verify(client).sendMessage(org.mockito.ArgumentMatchers.eq(42L),
                org.mockito.ArgumentMatchers.eq(BotTexts.CHOOSE_STUDENT),
                org.mockito.ArgumentMatchers.<List<List<BaleClient.InlineButton>>>any());
    }

    @Test
    void existingCommandsAnswerAsBefore() {
        link(42L, 7L);
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of(
                new MembershipInfo(1L, new UserId(7L), "a@b.c", "والد", 10L, "PARENT", true, true)));
        when(academic.listWards(7L, 10L)).thenReturn(List.of(
                new StudentInfo(new StudentId(100L), 1000L, 10L, null),
                new StudentInfo(new StudentId(200L), 2000L, 10L, null)));
        when(iam.findUser(new UserId(1000L)))
                .thenReturn(Optional.of(new UserProfile(new UserId(1000L), "s@b.c", "سارا", null)));
        when(iam.findUser(new UserId(2000L))).thenReturn(Optional.empty());
        when(school.findSchool(any())).thenReturn(Optional.empty());
        when(finance.listInvoicesForStudents(anyLong(), any())).thenReturn(List.of());

        handler.handle(textUpdate(42L, "فرزندان من"));

        verify(client).sendMessage(42L,
                "- سارا (مدرسه #10): 0 فاکتور صادرشده\n- دانش‌آموز #2000 (مدرسه #10): 0 فاکتور صادرشده");
    }

    @Test
    void linkConfirmationFallsBackToPlainTextWhenMenuFails() {
        when(iam.findUserByPhoneNumber("0912"))
                .thenReturn(Optional.of(new UserProfile(new UserId(7L), "a@b.c", "والد", "0912")));
        when(links.findByUserId(7L)).thenReturn(Optional.empty());
        link(42L, 7L);
        when(iam.findMembershipsByUser(new UserId(7L))).thenThrow(new IllegalStateException("boom"));

        handler.handle(new BaleUpdate(1L, new BaleMessage(1L, new BaleChat(42L), null,
                new BaleUpdate.BaleContact("0912")), null));

        verify(client).sendMessage(42L, BotTexts.linkSuccess("والد"));
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never()).sendMenu(any(), any(), any());
    }

    @Test
    void pureStudentHasNoInvoicesAndOwnIdCallbackIsNotAvailable() {
        link(42L, 7L);
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of(
                new MembershipInfo(1L, new UserId(7L), "a@b.c", "دانش‌آموز", 10L, "STUDENT", true, true)));
        when(academic.findMyEnrollment(7L, 10L)).thenReturn(Optional.of(
                new AcademicFacade.EnrollmentInfo(new StudentId(100L), 10L, null, null, null)));

        handler.handle(textUpdate(42L, "سلام"));
        handler.handle(callbackUpdate("cb9", 42L, "i:100"));

        org.mockito.ArgumentCaptor<List<List<String>>> menu = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(client).sendMenu(org.mockito.ArgumentMatchers.eq(42L),
                org.mockito.ArgumentMatchers.eq(BotTexts.UNKNOWN_TEXT), menu.capture());
        assertThat(menu.getValue().stream().flatMap(List::stream).toList())
                .contains(BotTexts.BTN_GRADES).doesNotContain(BotTexts.BTN_INVOICES);
        verify(client).sendMessage(42L, BotTexts.NOT_AVAILABLE);
        verifyNoInteractions(schoolAnswers);
    }
}
