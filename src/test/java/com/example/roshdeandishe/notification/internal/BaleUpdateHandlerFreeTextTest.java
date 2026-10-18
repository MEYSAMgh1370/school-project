package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.AcademicFacade.StudentId;
import com.example.roshdeandishe.academic.AcademicFacade.StudentInfo;
import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.iam.IamFacade.UserProfile;
import com.example.roshdeandishe.notification.internal.BaleUpdate.BaleChat;
import com.example.roshdeandishe.notification.internal.BaleUpdate.BaleMessage;
import com.example.roshdeandishe.notification.internal.BaleUpdate.BaleUser;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.SchoolFacade.SchoolInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BaleUpdateHandlerFreeTextTest {

    private static final long CHAT = 42L;
    private static final long PARENT = 7L;
    private static final long WARD_USER = 1000L;
    private static final long WARD_STUDENT = 555L;
    private static final long SCHOOL = 10L;

    private BaleLinkRepository links;
    private BaleClient client;
    private IamFacade iam;
    private SchoolFacade school;
    private AcademicFacade academic;
    private StudentAnswers studentAnswers;
    private FinanceFacade finance;
    private BaleUpdateHandler handler;
    private MockEnvironment env;

    @BeforeEach
    void setUp() {
        links = mock(BaleLinkRepository.class);
        client = mock(BaleClient.class);
        iam = mock(IamFacade.class);
        school = mock(SchoolFacade.class);
        academic = mock(AcademicFacade.class);
        SubjectResolver resolver = new SubjectResolver(academic, school, iam);
        studentAnswers = mock(StudentAnswers.class);
        BotRouter router = new BotRouter(resolver, studentAnswers, mock(TimetableAnswers.class), mock(SchoolAnswers.class));
        BotTokens tokens = mock(BotTokens.class);
        when(tokens.tokenFor(any())).thenReturn("tok");
        env = new MockEnvironment()
                .withProperty("roshdeandishe.bot.llm.enabled", "true")
                .withProperty("roshdeandishe.bot.llm.base-url", "https://llm.example")
                .withProperty("roshdeandishe.bot.llm.api-key", "k");
        finance = mock(FinanceFacade.class);
        handler = new BaleUpdateHandler(links, client, iam, school, finance,
                new BotContextLoader(links, iam, mock(SchoolBotRepository.class),school), resolver, router, tokens,
                new LlmSettings(env));
        when(academic.listWards(any(), any())).thenReturn(List.of());
        when(academic.findMyEnrollment(any(), any())).thenReturn(Optional.empty());
        when(school.findSchool(any())).thenReturn(Optional.empty());
    }

    private static BaleUpdate textUpdate(String text) {
        return new BaleUpdate(1L,
                new BaleMessage(1L, new BaleUser(CHAT), new BaleChat(CHAT, "private"), text, null), null);
    }

    /** والدِ «زهرا احمدی» با فرزندِ «علی رضایی» (studentId 555) در مدرسه‌ی «دبستان نیکان». */
    private void linkParentWithWard() {
        when(links.findByChat(BotRef.SHARED, CHAT))
                .thenReturn(Optional.of(new BaleLinkEntity(null, PARENT, CHAT, Instant.now())));
        when(iam.findUser(new UserId(PARENT)))
                .thenReturn(Optional.of(new UserProfile(new UserId(PARENT), "a@b.c", "زهرا احمدی", "09120000000")));
        when(iam.findUser(new UserId(WARD_USER)))
                .thenReturn(Optional.of(new UserProfile(new UserId(WARD_USER), "w@b.c", "علی رضایی", null)));
        when(iam.findMembershipsByUser(new UserId(PARENT))).thenReturn(List.of(
                new MembershipInfo(1L, new UserId(PARENT), "a@b.c", "زهرا احمدی", SCHOOL, "PARENT", true, true)));
        when(academic.listWards(PARENT, SCHOOL)).thenReturn(List.of(
                new StudentInfo(new StudentId(WARD_STUDENT), WARD_USER, SCHOOL, null)));
        SchoolInfo info = mock(SchoolInfo.class);
        when(info.name()).thenReturn("دبستان نیکان");
        when(school.findSchool(new SchoolId(SCHOOL))).thenReturn(Optional.of(info));
    }

    private FreeTextRequest request(Map<String, Long> labels) {
        return new FreeTextRequest(BotRef.SHARED, CHAT, PARENT, List.of("PARENT"), "نمره های [فرزند_۱]",
                EnumSet.of(BotIntent.GRADES), labels);
    }

    @Test
    void disabledLlmKeepsMenuReply() {
        env.setProperty("roshdeandishe.bot.llm.enabled", "false");
        linkParentWithWard();

        Optional<FreeTextRequest> result = handler.handle(BotRef.SHARED, textUpdate("نمره‌های علی چی شد؟"));

        assertThat(result).isEmpty();
        verify(client).sendMenu(eq("tok"), eq(CHAT), eq(BotTexts.UNKNOWN_TEXT), any());
    }

    @Test
    void unlinkedChatNeverBuildsRequest() {
        when(links.findByChat(BotRef.SHARED, CHAT)).thenReturn(Optional.empty());
        LlmClient llm = mock(LlmClient.class);
        FreeTextResponder responder = new FreeTextResponder(handler, llm, mock(LlmQuota.class),
                new LlmSettings(env), client, mock(BotTokens.class));
        BaleWebhookService service = new BaleWebhookService(handler, responder, Runnable::run,
                new RecentUpdateIds<>(10), mock(SchoolBotActivity.class));

        Optional<FreeTextRequest> result = handler.handle(BotRef.SHARED, textUpdate("نمره‌های علی چی شد؟"));
        service.processRawUpdate(BotRef.SHARED,
                "{\"update_id\":3,\"message\":{\"message_id\":1,\"chat\":{\"id\":42,\"type\":\"private\"},"
                        + "\"text\":\"نمره‌های علی چی شد؟\"}}");

        assertThat(result).isEmpty();
        verify(client, org.mockito.Mockito.times(2)).sendMessage("tok", CHAT, BotTexts.NOT_LINKED_TEXT);
        verifyNoInteractions(llm);
    }

    @Test
    void requestHoldsNoNamesIdsOrPhones() {
        linkParentWithWard();

        Optional<FreeTextRequest> result = handler.handle(BotRef.SHARED,
                textUpdate("نمره‌های علی رضایی چی شد؟ شماره‌ام ۰۹۱۲۳۴۵۶۷۸۹ دبستان نیکان"));

        assertThat(result).isPresent();
        FreeTextRequest request = result.get();
        assertThat(request.text()).doesNotContain("علی", "رضایی", "زهرا", "۰۹۱۲", "555", "نیکان");
        assertThat(request.labelToStudentId()).isEqualTo(Map.of("[فرزند_۱]", 555L));
        assertThat(request.allowed()).contains(BotIntent.GRADES, BotIntent.INVOICES);
        assertThat(request.activeRoleCodes()).containsExactly("PARENT");
        assertThat(request.toString()).doesNotContain(request.text());
        verify(client, never()).sendMenu(any(), any(), any(), any());
        verify(client, never()).sendMessage(any(), any(), any());
    }

    @Test
    void tooLongTextGetsMenuWithoutRequest() {
        linkParentWithWard();

        Optional<FreeTextRequest> result = handler.handle(BotRef.SHARED, textUpdate("ن".repeat(501)));

        assertThat(result).isEmpty();
        verify(client).sendMenu(eq("tok"), eq(CHAT), eq(BotTexts.UNKNOWN_TEXT), any());
    }

    @Test
    void forgedLabelNeverAnswersAnotherStudent() {
        linkParentWithWard();

        handler.answerFreeText(request(Map.of("[فرزند_۱]", WARD_STUDENT)),
                Optional.of(new ClassificationValidator.Resolved(BotIntent.GRADES, "[فرزند_۹]")));

        verify(client).sendMessage("tok", CHAT, BotTexts.NOT_AVAILABLE);
        verifyNoInteractions(studentAnswers);
    }

    @Test
    void wardRemovedDuringClassificationGetsNotAvailable() {
        linkParentWithWard();
        when(academic.listWards(PARENT, SCHOOL)).thenReturn(List.of(
                new StudentInfo(new StudentId(777L), 1001L, SCHOOL, null)));

        handler.answerFreeText(request(Map.of("[فرزند_۱]", WARD_STUDENT)),
                Optional.of(new ClassificationValidator.Resolved(BotIntent.GRADES, "[فرزند_۱]")));

        verify(client).sendMessage("tok", CHAT, BotTexts.NOT_AVAILABLE);
        verifyNoInteractions(studentAnswers);
    }

    @Test
    void resolvedLabelAnswersThatWard() {
        linkParentWithWard();
        when(studentAnswers.answer(eq(BotIntent.GRADES), any(), any())).thenReturn("نمره‌ها");

        handler.answerFreeText(request(Map.of("[فرزند_۱]", WARD_STUDENT)),
                Optional.of(new ClassificationValidator.Resolved(BotIntent.GRADES, "[فرزند_۱]")));

        verify(client).sendMessage("tok", CHAT, "نمره‌ها");
    }

    @Test
    void emptyResolutionGetsUnknownTextAndMenu() {
        linkParentWithWard();

        handler.answerFreeText(request(Map.of("[فرزند_۱]", WARD_STUDENT)), Optional.empty());

        verify(client).sendMenu(eq("tok"), eq(CHAT), eq(BotTexts.UNKNOWN_TEXT), any());
        verifyNoInteractions(studentAnswers);
    }

    @Test
    void unlinkedDuringClassificationGetsNotLinked() {
        when(links.findByChat(BotRef.SHARED, CHAT)).thenReturn(Optional.empty());

        handler.answerFreeText(request(Map.of("[فرزند_۱]", WARD_STUDENT)),
                Optional.of(new ClassificationValidator.Resolved(BotIntent.GRADES, "[فرزند_۱]")));

        verify(client).sendMessage("tok", CHAT, BotTexts.NOT_LINKED_TEXT);
        verifyNoInteractions(studentAnswers);
    }

    @Test
    void limitReachedSendsLimitTextWithMenu() {
        linkParentWithWard();

        handler.answerLimitReached(request(Map.of()));

        verify(client).sendMenu(eq("tok"), eq(CHAT), eq(BotTexts.LLM_LIMIT_REACHED), any());
    }
    /** دانش‌آموزِ «علی رضایی» (studentId 555) بدون فرزند: هیچ هدفِ WARD_TARGET (مالی) ندارد. */
    private void linkPureStudent() {
        when(links.findByChat(BotRef.SHARED, CHAT))
                .thenReturn(Optional.of(new BaleLinkEntity(null, WARD_USER, CHAT, Instant.now())));
        when(iam.findUser(new UserId(WARD_USER)))
                .thenReturn(Optional.of(new UserProfile(new UserId(WARD_USER), "w@b.c", "علی رضایی", null)));
        when(iam.findMembershipsByUser(new UserId(WARD_USER))).thenReturn(List.of(
                new MembershipInfo(2L, new UserId(WARD_USER), "w@b.c", "علی رضایی", SCHOOL, "STUDENT", true, true)));
        when(academic.findMyEnrollment(WARD_USER, SCHOOL)).thenReturn(Optional.of(
                new AcademicFacade.EnrollmentInfo(new StudentId(WARD_STUDENT), SCHOOL, null, null, null)));
    }

    /** معیارِ پذیرشِ ۳: پاسخِ INVOICES مدل به دانش‌آموز هرگز به مالی نمی‌رسد؛ منو یا «در دسترس نیست». */
    @Test
    void studentWhoseModelAnswersInvoicesNeverReachesFinance() {
        linkPureStudent();
        LlmClient llm = mock(LlmClient.class);
        ClassificationAnswer invoices = new ClassificationAnswer("INVOICES", 0.99, null, 0, "m");
        when(llm.classify(any())).thenReturn(invoices);
        LlmQuota quota = mock(LlmQuota.class);
        when(quota.tryAcquire(org.mockito.ArgumentMatchers.anyLong(), any())).thenReturn(true);
        FreeTextResponder responder = new FreeTextResponder(handler, llm, quota, new LlmSettings(env), client,
                mock(BotTokens.class));

        Optional<FreeTextRequest> request = handler.handle(BotRef.SHARED, textUpdate("صورتحساب‌های من چقدره؟"));

        assertThat(request).isPresent();
        assertThat(request.get().allowed()).contains(BotIntent.GRADES).doesNotContain(BotIntent.INVOICES);
        assertThat(ClassificationValidator.validate(invoices, request.get().allowed(), List.of(), 0.7)).isEmpty();

        responder.respond(request.get());
        verify(client).sendMenu(eq("tok"), eq(CHAT), eq(BotTexts.UNKNOWN_TEXT), any());

        // حتی اگر اعتبارسنج دور زده شود، روتر هدفِ مالی برای دانش‌آموز نمی‌سازد
        handler.answerFreeText(request.get(),
                Optional.of(new ClassificationValidator.Resolved(BotIntent.INVOICES, null)));
        verify(client).sendMessage("tok", CHAT, BotTexts.NOT_AVAILABLE);
        verifyNoInteractions(finance, studentAnswers);
    }
}
