package com.example.roshdeandishe.notification.internal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class BaleWebhookServiceTest {

    private static final String WITH_ID_9 = "{\"update_id\":9,\"message\":{\"message_id\":1,\"chat\":{\"id\":5},\"text\":\"x\"}}";
    private static final String NO_ID = "{\"message\":{\"message_id\":1,\"chat\":{\"id\":5},\"text\":\"x\"}}";

    private static final BotRef BOT = BotRef.school(3, 7);

    private final BaleUpdateHandler handler = mock(BaleUpdateHandler.class);
    private final Executor direct = Runnable::run;
    private final SchoolBotActivity activity = mock(SchoolBotActivity.class);

    private BaleWebhookService service(Executor executor) {
        return new BaleWebhookService(handler, executor, new RecentUpdateIds<>(100), activity);
    }

    @Test
    void handsTheParsedUpdateToTheHandler() {
        service(direct).processRawUpdate(BOT, WITH_ID_9);

        verify(handler).handle(eq(BOT), eq(new BaleUpdate(9L,
                new BaleUpdate.BaleMessage(1L, null, new BaleUpdate.BaleChat(5L, null), "x", null), null)));
    }

    @Test
    void sameUpdateIdIsHandledOnce() {
        var service = service(direct);
        service.processRawUpdate(BOT, WITH_ID_9);
        service.processRawUpdate(BOT, WITH_ID_9);

        verify(handler, times(1)).handle(any(), any());
    }

    @Test
    void updateWithoutIdIsAlwaysHandled() {
        var service = service(direct);
        service.processRawUpdate(BOT, NO_ID);
        service.processRawUpdate(BOT, NO_ID);

        verify(handler, times(2)).handle(any(), any());
    }

    @Test
    void malformedJsonIsIgnoredWithoutThrowing() {
        assertThatCode(() -> service(direct).processRawUpdate(BOT, "{not json")).doesNotThrowAnyException();
        verify(handler, never()).handle(any(), any());
    }

    @Test
    void returnsBeforeHandlingFinishes() {
        List<Runnable> held = new ArrayList<>();
        service(held::add).processRawUpdate(BOT, WITH_ID_9);

        assertThat(held).hasSize(1);
        verify(handler, never()).handle(any(), any());
        held.get(0).run();
        verify(handler).handle(any(), any());
    }

    @Test
    void fullPoolDropsTheUpdateWithoutThrowing() {
        Executor rejecting = task -> {
            throw new RejectedExecutionException("full");
        };
        assertThatCode(() -> service(rejecting).processRawUpdate(BOT, WITH_ID_9)).doesNotThrowAnyException();
        verify(handler, never()).handle(any(), any());
    }

    @Test
    void updatesOfOneChatAreHandledInOrder() {
        List<Runnable> held = new ArrayList<>();
        List<Long> handled = new ArrayList<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            handled.add(invocation.<BaleUpdate>getArgument(1).updateId());
            return null;
        }).when(handler).handle(any(), any());
        var service = service(held::add);
        service.processRawUpdate(BOT, "{\"update_id\":1,\"message\":{\"message_id\":1,\"chat\":{\"id\":5},\"text\":\"a\"}}");
        service.processRawUpdate(BOT, "{\"update_id\":2,\"message\":{\"message_id\":2,\"chat\":{\"id\":5},\"text\":\"b\"}}");

        // یک drain job برای هر چت؛ هر دو Update پشت سر هم در همان job اجرا می‌شوند
        assertThat(held).hasSize(1);
        held.get(0).run();
        assertThat(handled).containsExactly(1L, 2L);
    }

    @Test
    void anUpdateDroppedByAFullPoolIsHandledWhenItArrivesAgain() {
        AtomicBoolean full = new AtomicBoolean(true);
        Executor fullOnce = task -> {
            if (full.getAndSet(false)) {
                throw new RejectedExecutionException("full");
            }
            task.run();
        };
        var service = service(fullOnce);
        service.processRawUpdate(BOT, WITH_ID_9);
        service.processRawUpdate(BOT, WITH_ID_9);

        verify(handler, times(1)).handle(any(), any());
    }

    @Test
    void sameUpdateIdFromTwoBotsIsHandledTwice() {
        var service = service(direct);
        service.processRawUpdate(BotRef.school(3, 7), WITH_ID_9);
        service.processRawUpdate(BotRef.school(4, 8), WITH_ID_9);

        verify(handler, times(2)).handle(any(), any());
    }

    @Test
    void handlerReceivesTheResolvedBot() {
        service(direct).processRawUpdate(BotRef.school(3, 7), WITH_ID_9);

        verify(handler).handle(eq(BotRef.school(3, 7)), any());
    }

    @Test
    void oneBotIsCappedAtFortyQueuedUpdates() {
        List<Runnable> held = new ArrayList<>();
        var service = service(held::add);
        for (int i = 1; i <= 41; i++) {
            service.processRawUpdate(BOT, update(i, i));
        }

        // کلید ۴۱ام رد شد: هر چت یک job تخلیه (۴۰ چت)
        assertThat(held).hasSize(40);
        // ربات دیگر سهم خودش را دارد و پذیرفته می‌شود
        service.processRawUpdate(BotRef.school(4, 8), update(100, 100));
        assertThat(held).hasSize(41);
        held.forEach(Runnable::run);
        verify(handler, times(41)).handle(any(), any());
        // با خالی شدن صف، همان Update ۴۱ که فراموش شده بود دوباره قبول می‌شود
        held.clear();
        service.processRawUpdate(BOT, update(41, 41));
        held.forEach(Runnable::run);
        verify(handler, times(42)).handle(any(), any());
    }

    private static String update(long updateId, long chatId) {
        return "{\"update_id\":" + updateId + ",\"message\":{\"message_id\":1,\"chat\":{\"id\":" + chatId + "},\"text\":\"x\"}}";
    }

    @Test
    void touchesTheBotAfterASuccessfulEnqueueOnly() {
        service(direct).processRawUpdate(BOT, WITH_ID_9);
        verify(activity).touch(BOT);

        Executor rejecting = task -> {
            throw new RejectedExecutionException("full");
        };
        service(rejecting).processRawUpdate(BOT, NO_ID);
        service(direct).processRawUpdate(BOT, "{not json");
        verify(activity, times(1)).touch(any());
    }
}
