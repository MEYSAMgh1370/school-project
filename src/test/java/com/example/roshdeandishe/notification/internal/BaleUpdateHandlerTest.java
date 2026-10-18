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
import com.example.roshdeandishe.notification.internal.BaleUpdate.BaleContact;
import com.example.roshdeandishe.notification.internal.BaleUpdate.BaleMessage;
import com.example.roshdeandishe.notification.internal.BaleUpdate.BaleUser;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    private BotTokens tokens;
    private SchoolBotRepository schoolBots;

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
        tokens = mock(BotTokens.class);
        when(tokens.tokenFor(any())).thenReturn("tok");
        schoolBots = mock(SchoolBotRepository.class);
        handler = new BaleUpdateHandler(links, client, iam, school, finance,
                new BotContextLoader(links, iam, schoolBots, school), resolver, router, tokens);
        when(school.isBotAllowed(any(), any())).thenReturn(true);
        when(academic.listWards(any(), any())).thenReturn(List.of());
        when(academic.findMyEnrollment(any(), any())).thenReturn(Optional.empty());
    }

    private static BaleUpdate callbackUpdate(String id, Long chatId, String data) {
        return callbackUpdate(id, chatId, "private", data);
    }

    private static BaleUpdate callbackUpdate(String id, Long chatId, String chatType, String data) {
        BaleMessage message = new BaleMessage(1L, new BaleUser(chatId), new BaleChat(chatId, chatType), null, null);
        return new BaleUpdate(1L, null, new BaleCallbackQuery(id, message, data));
    }

    private static BaleUpdate textUpdate(Long chatId, String text) {
        return textUpdate(chatId, "private", text);
    }

    private static BaleUpdate textUpdate(Long chatId, String chatType, String text) {
        return new BaleUpdate(1L,
                new BaleMessage(1L, new BaleUser(chatId), new BaleChat(chatId, chatType), text, null), null);
    }

    private static BaleUpdate contactUpdate(Long chatId, String chatType, Long fromId, String phone, Long contactUserId) {
        return new BaleUpdate(1L, new BaleMessage(1L, fromId == null ? null : new BaleUser(fromId),
                new BaleChat(chatId, chatType), null, new BaleContact(phone, contactUserId)), null);
    }

    private void link(Long chatId, Long userId) {
        when(links.findByChat(BotRef.SHARED, chatId)).thenReturn(Optional.of(new BaleLinkEntity(null, userId, chatId, Instant.now())));
        when(iam.findUser(new UserId(userId)))
                .thenReturn(Optional.of(new UserProfile(new UserId(userId), "a@b.c", "والد", "0912")));
    }

    @Test
    void callbackFromUnlinkedChatIsAnsweredAndGetsNotLinkedText() {
        when(links.findByChat(BotRef.SHARED, 42L)).thenReturn(Optional.empty());

        handler.handle(BotRef.SHARED, callbackUpdate("cb1", 42L, "X"));

        verify(client).answerCallbackQuery("tok", "cb1");
        verify(client).sendMessage("tok", 42L, NOT_LINKED);
    }

    @Test
    void malformedCallbackFromLinkedChatIsAnsweredAndNotAvailable() {
        link(42L, 7L);
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of());

        handler.handle(BotRef.SHARED, callbackUpdate("cb2", 42L, "X"));

        verify(client).answerCallbackQuery("tok", "cb2");
        verify(client).sendMessage("tok", 42L, BotTexts.NOT_AVAILABLE);
    }

    @Test
    void callbackForAForeignStudentIsNotAvailableAndCallsNoAnswerClass() {
        link(42L, 7L);
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of(
                new MembershipInfo(1L, new UserId(7L), "a@b.c", "والد", 10L, "PARENT", true, true)));
        when(academic.listWards(7L, 10L)).thenReturn(List.of(
                new StudentInfo(new StudentId(100L), 1000L, 10L, null)));
        when(school.findSchool(any())).thenReturn(Optional.empty());

        handler.handle(BotRef.SHARED, callbackUpdate("cb3", 42L, "g:999"));

        verify(client).sendMessage("tok", 42L, BotTexts.NOT_AVAILABLE);
        verifyNoInteractions(studentAnswers);
    }

    @Test
    void unknownTextShowsTheMenu() {
        link(42L, 7L);
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of());

        handler.handle(BotRef.SHARED, textUpdate(42L, "سلام"));

        verify(client).sendMenu("tok", 42L, BotTexts.UNKNOWN_TEXT, List.of(List.of(BotTexts.BTN_HELP)));
    }

    @Test
    void helpAlsoSendsTheMenu() {
        link(42L, 7L);
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of());

        handler.handle(BotRef.SHARED, textUpdate(42L, "راهنما"));

        verify(client).sendMenu(eq("tok"), eq(42L), argThat(text -> text.startsWith(BotTexts.HELP_NOTHING_AVAILABLE)
                && !text.contains(BotTexts.BTN_INVOICES) && !text.contains(BotTexts.CMD_MY_WARDS)),
                eq(List.of(List.of(BotTexts.BTN_HELP))));
    }

    @Test
    void linkingSendsTheMenu() {
        when(iam.findUserByPhoneNumber("0912"))
                .thenReturn(Optional.of(new UserProfile(new UserId(7L), "a@b.c", "والد", "0912")));
        when(links.findByUser(BotRef.SHARED, 7L)).thenReturn(Optional.empty());
        link(42L, 7L);
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of());

        handler.handle(BotRef.SHARED, contactUpdate(42L, "private", 42L, "0912", 42L));

        verify(client).sendMenu("tok", 42L, BotTexts.linkSuccess("والد"), List.of(List.of(BotTexts.BTN_HELP)));
    }

    @Test
    void ownContactInPrivateChatLinks() {
        when(iam.findUserByPhoneNumber("0912"))
                .thenReturn(Optional.of(new UserProfile(new UserId(7L), "a@b.c", "والد", "0912")));
        when(links.findByUser(BotRef.SHARED, 7L)).thenReturn(Optional.empty());
        link(42L, 7L);
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of());

        handler.handle(BotRef.SHARED, contactUpdate(42L, "private", 555L, "0912", 555L));

        verify(links).save(any(BaleLinkEntity.class));
        verify(client).sendMenu("tok", 42L, BotTexts.linkSuccess("والد"), List.of(List.of(BotTexts.BTN_HELP)));
    }

    @Test
    void someoneElsesContactIsRejectedAndLinksNothing() {
        handler.handle(BotRef.SHARED, contactUpdate(42L, "private", 555L, "0912", 556L));

        verify(client).sendMessage("tok", 42L, BotTexts.CONTACT_NOT_OWN);
        verify(links, never()).save(any());
        verifyNoInteractions(iam);
    }

    @Test
    void contactWithoutUserIdIsRejected() {
        handler.handle(BotRef.SHARED, contactUpdate(42L, "private", 555L, "0912", null));

        verify(client).sendMessage("tok", 42L, BotTexts.CONTACT_NOT_OWN);
        verify(links, never()).save(any());
        verifyNoInteractions(iam);
    }

    @Test
    void contactWithoutSenderIsRejected() {
        handler.handle(BotRef.SHARED, contactUpdate(42L, "private", null, "0912", 555L));

        verify(client).sendMessage("tok", 42L, BotTexts.CONTACT_NOT_OWN);
        verify(links, never()).save(any());
    }

    @Test
    void existingLinkOfThePhoneOwnerIsNotMovedByAForeignContact() {
        BaleLinkEntity existing = new BaleLinkEntity(null, 7L, 100L, Instant.now());
        when(links.findByUser(BotRef.SHARED, 7L)).thenReturn(Optional.of(existing));
        when(iam.findUserByPhoneNumber("0912"))
                .thenReturn(Optional.of(new UserProfile(new UserId(7L), "a@b.c", "والد", "0912")));

        handler.handle(BotRef.SHARED, contactUpdate(42L, "private", 555L, "0912", 556L));

        assertThat(existing.getChatId()).isEqualTo(100L);
        verify(links, never()).save(any());
        verify(links, never()).findByUser(any(), anyLong());
        verify(client).sendMessage("tok", 42L, BotTexts.CONTACT_NOT_OWN);
    }

    @Test
    void groupChatGetsPrivateOnlyAndNoData() {
        link(42L, 7L);

        handler.handle(BotRef.SHARED, textUpdate(42L, "group", "فرزندان من"));

        verify(client).sendMessage("tok", 42L, BotTexts.PRIVATE_CHAT_ONLY);
        verifyNoInteractions(finance, academic, school);
        verify(client, never()).sendMenu(any(), any(), any(), any());
        verify(client, never()).sendContactRequest(any(), any(), any());
    }

    @Test
    void startInGroupGetsPrivateOnlyWithoutContactKeyboard() {
        handler.handle(BotRef.SHARED, textUpdate(42L, "group", "/start"));

        verify(client).sendMessage("tok", 42L, BotTexts.PRIVATE_CHAT_ONLY);
        verify(client, never()).sendContactRequest(any(), any(), any());
    }

    @Test
    void contactInGroupLinksNothing() {
        handler.handle(BotRef.SHARED, contactUpdate(42L, "group", 555L, "0912", 555L));

        verify(client).sendMessage("tok", 42L, BotTexts.PRIVATE_CHAT_ONLY);
        verify(links, never()).save(any());
        verifyNoInteractions(iam);
    }

    @Test
    void callbackInGroupIsAnsweredThenPrivateOnly() {
        link(42L, 7L);

        handler.handle(BotRef.SHARED, callbackUpdate("cbg", 42L, "group", "g:100"));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(client);
        order.verify(client).answerCallbackQuery("tok", "cbg");
        order.verify(client).sendMessage("tok", 42L, BotTexts.PRIVATE_CHAT_ONLY);
        verifyNoInteractions(studentAnswers, schoolAnswers, academic, finance);
    }

    @Test
    void missingChatTypeIsTreatedAsNotPrivate() {
        link(42L, 7L);

        handler.handle(BotRef.SHARED, textUpdate(42L, null, "فرزندان من"));

        verify(client).sendMessage("tok", 42L, BotTexts.PRIVATE_CHAT_ONLY);
        verifyNoInteractions(finance, academic);
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

        handler.handle(BotRef.SHARED, textUpdate(42L, BotTexts.BTN_GRADES));

        verify(client).sendMessage(org.mockito.ArgumentMatchers.eq("tok"), org.mockito.ArgumentMatchers.eq(42L),
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

        handler.handle(BotRef.SHARED, textUpdate(42L, "فرزندان من"));

        verify(client).sendMessage("tok", 42L,
                "- سارا (مدرسه #10): 0 فاکتور صادرشده\n- دانش‌آموز #2000 (مدرسه #10): 0 فاکتور صادرشده");
    }

    @Test
    void linkConfirmationFallsBackToPlainTextWhenMenuFails() {
        when(iam.findUserByPhoneNumber("0912"))
                .thenReturn(Optional.of(new UserProfile(new UserId(7L), "a@b.c", "والد", "0912")));
        when(links.findByUser(BotRef.SHARED, 7L)).thenReturn(Optional.empty());
        link(42L, 7L);
        when(iam.findMembershipsByUser(new UserId(7L))).thenThrow(new IllegalStateException("boom"));

        handler.handle(BotRef.SHARED, contactUpdate(42L, "private", 42L, "0912", 42L));

        verify(client).sendMessage("tok", 42L, BotTexts.linkSuccess("والد"));
        org.mockito.Mockito.verify(client, never()).sendMenu(any(), any(), any(), any());
    }

    @Test
    void pureStudentHasNoInvoicesAndOwnIdCallbackIsNotAvailable() {
        link(42L, 7L);
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of(
                new MembershipInfo(1L, new UserId(7L), "a@b.c", "دانش‌آموز", 10L, "STUDENT", true, true)));
        when(academic.findMyEnrollment(7L, 10L)).thenReturn(Optional.of(
                new AcademicFacade.EnrollmentInfo(new StudentId(100L), 10L, null, null, null)));

        handler.handle(BotRef.SHARED, textUpdate(42L, "سلام"));
        handler.handle(BotRef.SHARED, callbackUpdate("cb9", 42L, "i:100"));

        org.mockito.ArgumentCaptor<List<List<String>>> menu = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(client).sendMenu(org.mockito.ArgumentMatchers.eq("tok"), org.mockito.ArgumentMatchers.eq(42L),
                org.mockito.ArgumentMatchers.eq(BotTexts.UNKNOWN_TEXT), menu.capture());
        assertThat(menu.getValue().stream().flatMap(List::stream).toList())
                .contains(BotTexts.BTN_GRADES).doesNotContain(BotTexts.BTN_INVOICES);
        verify(client).sendMessage("tok", 42L, BotTexts.NOT_AVAILABLE);
        verifyNoInteractions(schoolAnswers);
    }

    // ---------------------------------------------------------------- تنظیمات بات مدرسه

    private static final String BOT_OFF = "بات برای مدرسه‌ی شما فعال نیست.";

    private void parentOfDisabledSchool() {
        link(42L, 7L);
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of(
                new MembershipInfo(1L, new UserId(7L), "a@b.c", "والد", 10L, "PARENT", true, true)));
        when(school.isBotAllowed(new SchoolId(10L), Set.of("PARENT"))).thenReturn(false);
    }

    @Test
    void blockedUserGetsExactlyTheDisabledTextForFreeText() {
        parentOfDisabledSchool();

        handler.handle(BotRef.SHARED, textUpdate(42L, "نمره‌های سارا چند است؟"));

        verify(client).sendMessage("tok", 42L, BOT_OFF);
        verifyNoInteractions(studentAnswers, schoolAnswers);
        verify(client, never()).sendMenu(any(), anyLong(), any(), any());
    }

    @Test
    void blockedUserGetsTheDisabledTextForCallbacks() {
        parentOfDisabledSchool();

        handler.handle(BotRef.SHARED, callbackUpdate("cb20", 42L, "g:100"));

        verify(client).answerCallbackQuery("tok", "cb20");
        verify(client).sendMessage("tok", 42L, BOT_OFF);
        verifyNoInteractions(studentAnswers);
    }

    @Test
    void blockedUserGetsTheDisabledTextForMySchoolsAndWards() {
        parentOfDisabledSchool();

        handler.handle(BotRef.SHARED, textUpdate(42L, "مدرسه‌های من"));
        handler.handle(BotRef.SHARED, textUpdate(42L, "فرزندان من"));

        verify(client, org.mockito.Mockito.times(2)).sendMessage("tok", 42L, BOT_OFF);
    }

    @Test
    void helpAlwaysWorks() {
        parentOfDisabledSchool();

        handler.handle(BotRef.SHARED, textUpdate(42L, "راهنما"));

        verify(client).sendMenu(eq("tok"), eq(42L), argThat(text -> text.startsWith(BotTexts.HELP_NOTHING_AVAILABLE)),
                eq(List.of(List.of(BotTexts.BTN_HELP))));
        verify(client, never()).sendMessage("tok", 42L, BOT_OFF);

        // کاربر مجاز بدون تغییر
        when(school.isBotAllowed(new SchoolId(10L), Set.of("PARENT"))).thenReturn(true);
        handler.handle(BotRef.SHARED, textUpdate(42L, "راهنما"));
        verify(client, org.mockito.Mockito.times(2)).sendMenu(eq("tok"), eq(42L), any(), any());
    }

    @Test
    void startAndContactLinkingAreNotBlocked() {
        parentOfDisabledSchool();
        when(iam.findUserByPhoneNumber("0912"))
                .thenReturn(Optional.of(new UserProfile(new UserId(7L), "a@b.c", "والد", "0912")));
        when(links.findByUser(BotRef.SHARED, 7L)).thenReturn(Optional.empty());

        handler.handle(BotRef.SHARED, textUpdate(42L, "/start"));
        handler.handle(BotRef.SHARED, contactUpdate(42L, "private", 42L, "0912", 42L));

        verify(client).sendContactRequest("tok", 42L, BotTexts.WELCOME_TEXT);
        verify(client).sendMenu(eq("tok"), eq(42L), eq(BotTexts.linkSuccess("والد")), any());
        verify(client, never()).sendMessage("tok", 42L, BOT_OFF);
    }

    @Test
    void allowedUserIsUnaffected() {
        link(42L, 7L);
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of(
                new MembershipInfo(1L, new UserId(7L), "a@b.c", "والد", 10L, "PARENT", true, true)));
        when(school.isBotAllowed(new SchoolId(10L), Set.of("PARENT"))).thenReturn(true);

        handler.handle(BotRef.SHARED, textUpdate(42L, "سلام"));

        verify(client).sendMenu(eq("tok"), eq(42L), eq(BotTexts.UNKNOWN_TEXT), any());
        verify(client, never()).sendMessage("tok", 42L, BOT_OFF);
    }

    @Test
    void mySchoolsListsOnlyAllowedSchools() {
        link(42L, 7L);
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of(
                new MembershipInfo(1L, new UserId(7L), "a@b.c", "والد", 10L, "PARENT", true, true),
                new MembershipInfo(2L, new UserId(7L), "a@b.c", "والد", 20L, "PARENT", true, true)));
        when(school.isBotAllowed(new SchoolId(10L), Set.of("PARENT"))).thenReturn(false);
        when(school.findSchool(new SchoolId(20L))).thenReturn(Optional.empty());

        handler.handle(BotRef.SHARED, textUpdate(42L, "مدرسه‌های من"));

        org.mockito.ArgumentCaptor<String> text = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(client).sendMessage(eq("tok"), org.mockito.ArgumentMatchers.eq(42L), text.capture());
        assertThat(text.getValue()).contains(BotTexts.schoolFallback(20L)).doesNotContain(BotTexts.schoolFallback(10L));
    }

    private static final BotRef SCHOOL_BOT = BotRef.school(3, 7);

    private static MembershipInfo membershipIn(Long userId, Long schoolId, boolean active) {
        return new MembershipInfo(1L, new UserId(userId), "a@b.c", "والد", schoolId, "PARENT", active, true);
    }

    private void phoneOf7() {
        when(iam.findUserByPhoneNumber("0912"))
                .thenReturn(Optional.of(new UserProfile(new UserId(7L), "a@b.c", "والد", "0912")));
    }

    @Test
    void schoolBotRepliesWithItsOwnToken() {
        when(tokens.tokenFor(SCHOOL_BOT)).thenReturn("school-token");
        when(links.findByChat(SCHOOL_BOT, 42L)).thenReturn(Optional.empty());

        handler.handle(SCHOOL_BOT, textUpdate(42L, "سلام"));

        verify(client).sendMessage("school-token", 42L, NOT_LINKED);
        verify(client, never()).sendMessage(eq("tok"), anyLong(), any());
    }

    @Test
    void nonMemberContactInSchoolBotIsRefused() {
        phoneOf7();
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of(membershipIn(7L, 8L, true)));
        when(iam.isPlatformAdmin(new UserId(7L))).thenReturn(false);

        handler.handle(SCHOOL_BOT, contactUpdate(42L, "private", 555L, "0912", 555L));

        verify(client).sendMessage("tok", 42L, BotTexts.NOT_A_MEMBER);
        verify(links, never()).save(any());
    }

    @Test
    void endedMembershipDoesNotAllowLinkingInSchoolBot() {
        phoneOf7();
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of(membershipIn(7L, 7L, false)));

        handler.handle(SCHOOL_BOT, contactUpdate(42L, "private", 555L, "0912", 555L));

        verify(client).sendMessage("tok", 42L, BotTexts.NOT_A_MEMBER);
        verify(links, never()).save(any());
    }

    @Test
    void memberContactInSchoolBotLinksWithBotId() {
        phoneOf7();
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of(membershipIn(7L, 7L, true)));
        when(links.findByUser(SCHOOL_BOT, 7L)).thenReturn(Optional.empty());

        handler.handle(SCHOOL_BOT, contactUpdate(42L, "private", 555L, "0912", 555L));

        org.mockito.ArgumentCaptor<BaleLinkEntity> saved = org.mockito.ArgumentCaptor.forClass(BaleLinkEntity.class);
        verify(links).save(saved.capture());
        assertThat(saved.getValue().getBotId()).isEqualTo(3L);
        assertThat(saved.getValue().getUserId()).isEqualTo(7L);
        assertThat(saved.getValue().getChatId()).isEqualTo(42L);
    }

    @Test
    void platformAdminMayLinkToAnySchoolBot() {
        phoneOf7();
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of());
        when(iam.isPlatformAdmin(new UserId(7L))).thenReturn(true);
        when(links.findByUser(SCHOOL_BOT, 7L)).thenReturn(Optional.empty());

        handler.handle(SCHOOL_BOT, contactUpdate(42L, "private", 555L, "0912", 555L));

        org.mockito.ArgumentCaptor<BaleLinkEntity> saved = org.mockito.ArgumentCaptor.forClass(BaleLinkEntity.class);
        verify(links).save(saved.capture());
        assertThat(saved.getValue().getBotId()).isEqualTo(3L);
    }

    @Test
    void sameChatIdIsLinkedSeparatelyPerBot() {
        BaleLinkEntity sharedLink = new BaleLinkEntity(null, 7L, 42L, Instant.now());
        when(links.findByUser(BotRef.SHARED, 7L)).thenReturn(Optional.of(sharedLink));
        when(links.findByUser(SCHOOL_BOT, 7L)).thenReturn(Optional.empty());
        phoneOf7();
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of(membershipIn(7L, 7L, true)));

        handler.handle(SCHOOL_BOT, contactUpdate(42L, "private", 555L, "0912", 555L));

        org.mockito.ArgumentCaptor<BaleLinkEntity> saved = org.mockito.ArgumentCaptor.forClass(BaleLinkEntity.class);
        verify(links).save(saved.capture());
        assertThat(saved.getValue()).isNotSameAs(sharedLink);
        assertThat(saved.getValue().getBotId()).isEqualTo(3L);
        assertThat(sharedLink.getBotId()).isNull();
        assertThat(sharedLink.getChatId()).isEqualTo(42L);
    }

    @Test
    void ownershipIsStillCheckedBeforeMembership() {
        handler.handle(SCHOOL_BOT, contactUpdate(42L, "private", 555L, "0912", 556L));

        verify(client).sendMessage("tok", 42L, BotTexts.CONTACT_NOT_OWN);
        verify(links, never()).save(any());
        verifyNoInteractions(iam);
    }

    @Test
    void sharedBotLinkingNeedsNoMembership() {
        phoneOf7();
        when(links.findByUser(BotRef.SHARED, 7L)).thenReturn(Optional.empty());
        link(42L, 7L);
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of());

        handler.handle(BotRef.SHARED, contactUpdate(42L, "private", 555L, "0912", 555L));

        verify(links).save(any(BaleLinkEntity.class));
        verify(iam, never()).isPlatformAdmin(any());
    }

    // ---------------------------------------------------------------- دامنه‌ی ربات × تنظیمات بات مدرسه

    @Test
    void schoolBotUserBlockedBySettingsGetsTheDisabledTextWithTheSchoolBotToken() {
        when(tokens.tokenFor(SCHOOL_BOT)).thenReturn("school-token");
        when(links.findByChat(SCHOOL_BOT, 42L)).thenReturn(Optional.of(new BaleLinkEntity(3L, 7L, 42L, Instant.now())));
        when(iam.findUser(new UserId(7L)))
                .thenReturn(Optional.of(new UserProfile(new UserId(7L), "a@b.c", "والد", "0912")));
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of(membershipIn(7L, 7L, true)));
        when(school.isBotAllowed(new SchoolId(7L), Set.of("PARENT"))).thenReturn(false);

        handler.handle(SCHOOL_BOT, textUpdate(42L, "سلام"));

        verify(client).sendMessage("school-token", 42L, BOT_OFF);
        verify(client, never()).sendMenu(any(), anyLong(), any(), any());
    }

    @Test
    void sharedBotUserWhoseOnlySchoolHasItsOwnBotKeepsTheNoMembershipBehaviour() {
        link(42L, 7L);
        when(schoolBots.findActiveSchoolIds()).thenReturn(Set.of(10L));
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of(membershipIn(7L, 10L, true)));
        when(school.isBotAllowed(new SchoolId(10L), Set.of("PARENT"))).thenReturn(false);

        handler.handle(BotRef.SHARED, textUpdate(42L, "سلام"));

        verify(client).sendMenu("tok", 42L, BotTexts.UNKNOWN_TEXT, List.of(List.of(BotTexts.BTN_HELP)));
        verify(client, never()).sendMessage("tok", 42L, BOT_OFF);
    }
}
