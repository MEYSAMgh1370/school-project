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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BaleUpdateHandlerTest {

    private static final String NOT_LINKED = "حساب شما هنوز متصل نشده است. لطفاً ابتدا /start را ارسال کنید.";
    private static final String HELP = """
            دستورهای قابل استفاده:
            «مدرسه‌های من» - فهرست مدرسه‌ها و نقش‌های شما
            «فرزندان من» - فهرست فرزندان لینک‌شده و وضعیت مالی هرکدام""";

    private BaleLinkRepository links;
    private BaleClient client;
    private IamFacade iam;
    private SchoolFacade school;
    private AcademicFacade academic;
    private FinanceFacade finance;
    private BaleUpdateHandler handler;

    @BeforeEach
    void setUp() {
        links = mock(BaleLinkRepository.class);
        client = mock(BaleClient.class);
        iam = mock(IamFacade.class);
        school = mock(SchoolFacade.class);
        academic = mock(AcademicFacade.class);
        finance = mock(FinanceFacade.class);
        handler = new BaleUpdateHandler(links, client, iam, school, finance,
                new BotContextLoader(links, iam, academic));
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
    void callbackFromLinkedChatIsAnsweredAndGetsHelp() {
        link(42L, 7L);
        when(iam.findMembershipsByUser(new UserId(7L))).thenReturn(List.of());

        handler.handle(callbackUpdate("cb2", 42L, "X"));

        verify(client).answerCallbackQuery("cb2");
        verify(client).sendMessage(42L, HELP);
    }

    @Test
    void myWardsTextGivesTheSameAnswerAsBefore() {
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
}
