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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class BaleWebhookServiceTest {

    private static final String WITH_ID_9 = "{\"update_id\":9,\"message\":{\"message_id\":1,\"chat\":{\"id\":5},\"text\":\"x\"}}";
    private static final String NO_ID = "{\"message\":{\"message_id\":1,\"chat\":{\"id\":5},\"text\":\"x\"}}";

    private final BaleUpdateHandler handler = mock(BaleUpdateHandler.class);
    private final Executor direct = Runnable::run;

    private BaleWebhookService service(Executor executor) {
        return new BaleWebhookService(handler, executor, new RecentUpdateIds(100));
    }

    @Test
    void handsTheParsedUpdateToTheHandler() {
        service(direct).processRawUpdate(WITH_ID_9);

        verify(handler).handle(new BaleUpdate(9L,
                new BaleUpdate.BaleMessage(1L, new BaleUpdate.BaleChat(5L), "x", null), null));
    }

    @Test
    void sameUpdateIdIsHandledOnce() {
        var service = service(direct);
        service.processRawUpdate(WITH_ID_9);
        service.processRawUpdate(WITH_ID_9);

        verify(handler, times(1)).handle(any());
    }

    @Test
    void updateWithoutIdIsAlwaysHandled() {
        var service = service(direct);
        service.processRawUpdate(NO_ID);
        service.processRawUpdate(NO_ID);

        verify(handler, times(2)).handle(any());
    }

    @Test
    void malformedJsonIsIgnoredWithoutThrowing() {
        assertThatCode(() -> service(direct).processRawUpdate("{not json")).doesNotThrowAnyException();
        verify(handler, never()).handle(any());
    }

    @Test
    void returnsBeforeHandlingFinishes() {
        List<Runnable> held = new ArrayList<>();
        service(held::add).processRawUpdate(WITH_ID_9);

        assertThat(held).hasSize(1);
        verify(handler, never()).handle(any());
        held.get(0).run();
        verify(handler).handle(any());
    }

    @Test
    void fullPoolDropsTheUpdateWithoutThrowing() {
        Executor rejecting = task -> {
            throw new RejectedExecutionException("full");
        };
        assertThatCode(() -> service(rejecting).processRawUpdate(WITH_ID_9)).doesNotThrowAnyException();
        verify(handler, never()).handle(any());
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
        service.processRawUpdate(WITH_ID_9);
        service.processRawUpdate(WITH_ID_9);

        verify(handler, times(1)).handle(any());
    }
}
