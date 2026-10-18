package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.notification.NotificationFacade;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * مسیریابی پیام از NotificationFacade به کانال‌ها، بدون Spring و Docker.
 * کانال‌ها fake هستند؛ رفتار واقعی بله در BaleFlowIntegrationTest و
 * BaleChannelTest پوشش داده می‌شود.
 */
class NotificationFacadeImplTest {

    private static class FakeChannel implements NotificationChannel {
        private final boolean result;
        private final List<String> calls = new ArrayList<>();

        FakeChannel(boolean result) {
            this.result = result;
        }

        @Override
        public boolean send(Long userId, String text) {
            calls.add(userId + ":" + text);
            return result;
        }
    }

    /** مدرسه‌ی ساختگی که اعلان‌هایش روشن است (پیش‌فرض)؛ سوئیچ در NotificationsSwitchTest تست می‌شود. */
    private static com.example.roshdeandishe.school.SchoolFacade enabledSchools() {
        com.example.roshdeandishe.school.SchoolFacade school = mock(com.example.roshdeandishe.school.SchoolFacade.class);
        when(school.isNotificationsEnabled(any())).thenReturn(true);
        return school;
    }

    private static NotificationFacadeImpl facade(NotificationChannel... channels) {
        // webhookService در مسیر sendMessageToUser استفاده نمی‌شود
        return new NotificationFacadeImpl(List.of(channels), null, null, enabledSchools());
    }

    @Test
    void returnsTrueWhenTheOnlyChannelDelivers() {
        FakeChannel bale = new FakeChannel(true);

        assertThat(facade(bale).sendMessageToUser(7L, "سلام")).isTrue();
        assertThat(bale.calls).containsExactly("7:سلام");
    }

    @Test
    void returnsFalseWhenTheOnlyChannelCannotDeliver() {
        assertThat(facade(new FakeChannel(false)).sendMessageToUser(7L, "سلام")).isFalse();
    }

    @Test
    void returnsFalseWithoutAnyChannel() {
        assertThat(facade().sendMessageToUser(7L, "سلام")).isFalse();
    }

    @Test
    void stopsAtTheFirstChannelThatDelivers() {
        FakeChannel first = new FakeChannel(true);
        FakeChannel second = new FakeChannel(true);

        assertThat(facade(first, second).sendMessageToUser(7L, "سلام")).isTrue();
        assertThat(first.calls).hasSize(1);
        assertThat(second.calls).isEmpty();
    }

    @Test
    void fallsThroughToTheNextChannelWhenOneCannotDeliver() {
        FakeChannel unreachable = new FakeChannel(false);
        FakeChannel reachable = new FakeChannel(true);

        assertThat(facade(unreachable, reachable).sendMessageToUser(7L, "سلام")).isTrue();
        assertThat(unreachable.calls).hasSize(1);
        assertThat(reachable.calls).hasSize(1);
    }

    // ---- deliverMessageToUser (نتیجه‌ی دقیق) ----

    private static class ResultChannel implements NotificationChannel {
        private final NotificationFacade.DeliveryResult result;
        int calls;

        ResultChannel(NotificationFacade.DeliveryResult result) {
            this.result = result;
        }

        @Override
        public boolean send(Long userId, String text) {
            return deliver(userId, text) == NotificationFacade.DeliveryResult.SENT;
        }

        @Override
        public NotificationFacade.DeliveryResult deliver(Long userId, String text) {
            calls++;
            return result;
        }
    }

    @Test
    void theSchoolIsPassedToTheChannelAndTwoArgumentCallsRouteWithoutSchool() {
        List<String> seen = new ArrayList<>();
        NotificationChannel channel = new NotificationChannel() {
            @Override
            public boolean send(Long userId, String text) {
                return false;
            }

            @Override
            public NotificationFacade.DeliveryResult deliver(Long userId, Long schoolId, String text) {
                seen.add(userId + ":" + schoolId + ":" + text);
                return NotificationFacade.DeliveryResult.SENT;
            }
        };

        assertThat(facade(channel).sendMessageToUser(7L, 5L, "x")).isTrue();
        assertThat(facade(channel).deliverMessageToUser(7L, "y")).isEqualTo(NotificationFacade.DeliveryResult.SENT);
        assertThat(seen).containsExactly("7:5:x", "7:null:y");
    }

    @Test
    void deliverStopsAtTheFirstSentChannel() {
        ResultChannel first = new ResultChannel(NotificationFacade.DeliveryResult.SENT);
        ResultChannel second = new ResultChannel(NotificationFacade.DeliveryResult.SENT);

        assertThat(facade(first, second).deliverMessageToUser(7L, "سلام"))
                .isEqualTo(NotificationFacade.DeliveryResult.SENT);
        assertThat(second.calls).isZero();
    }

    @Test
    void withoutAnyChannelTheUserIsNotLinked() {
        assertThat(facade().deliverMessageToUser(7L, "سلام")).isEqualTo(NotificationFacade.DeliveryResult.NOT_LINKED);
    }

    @Test
    void theWorstUnsuccessfulResultWinsSoATemporaryFailureIsNeverHidden() {
        NotificationFacade.DeliveryResult notLinked = NotificationFacade.DeliveryResult.NOT_LINKED;
        NotificationFacade.DeliveryResult temporary = NotificationFacade.DeliveryResult.TEMPORARY_FAILURE;
        NotificationFacade.DeliveryResult undeliverable = NotificationFacade.DeliveryResult.UNDELIVERABLE;

        assertThat(facade(new ResultChannel(notLinked), new ResultChannel(temporary)).deliverMessageToUser(7L, "x"))
                .isEqualTo(temporary);
        assertThat(facade(new ResultChannel(temporary), new ResultChannel(notLinked)).deliverMessageToUser(7L, "x"))
                .isEqualTo(temporary);
        assertThat(facade(new ResultChannel(notLinked), new ResultChannel(undeliverable)).deliverMessageToUser(7L, "x"))
                .isEqualTo(undeliverable);
        assertThat(facade(new ResultChannel(undeliverable), new ResultChannel(notLinked)).deliverMessageToUser(7L, "x"))
                .isEqualTo(undeliverable);
    }

    @Test
    void aChannelThatOnlyKnowsBooleanSendIsTreatedAsFinalWhenItFails() {
        assertThat(facade(new FakeChannel(true)).deliverMessageToUser(7L, "x"))
                .isEqualTo(NotificationFacade.DeliveryResult.SENT);
        assertThat(facade(new FakeChannel(false)).deliverMessageToUser(7L, "x"))
                .isEqualTo(NotificationFacade.DeliveryResult.UNDELIVERABLE);
    }

    @Test
    void sendMessageToUserIsTrueOnlyForSent() {
        assertThat(facade(new ResultChannel(NotificationFacade.DeliveryResult.TEMPORARY_FAILURE))
                .sendMessageToUser(7L, "x")).isFalse();
        assertThat(facade(new ResultChannel(NotificationFacade.DeliveryResult.SENT))
                .sendMessageToUser(7L, "x")).isTrue();
    }

    @Test
    void unknownSecretIsRejectedWithoutParsing() {
        BotResolver resolver = mock(BotResolver.class);
        BaleWebhookService webhookService = mock(BaleWebhookService.class);
        when(resolver.resolve("nope")).thenReturn(Optional.empty());

        boolean accepted = new NotificationFacadeImpl(List.of(), webhookService, resolver, enabledSchools())
                .processIncomingUpdate("nope", "{}");

        assertThat(accepted).isFalse();
        verify(webhookService, never()).processRawUpdate(any(), any());
    }

    @Test
    void knownSecretHandsTheBodyToTheWebhookServiceWithItsBot() {
        BotResolver resolver = mock(BotResolver.class);
        BaleWebhookService webhookService = mock(BaleWebhookService.class);
        when(resolver.resolve("ok")).thenReturn(Optional.of(BotRef.SHARED));

        boolean accepted = new NotificationFacadeImpl(List.of(), webhookService, resolver, enabledSchools())
                .processIncomingUpdate("ok", "{}");

        assertThat(accepted).isTrue();
        verify(webhookService).processRawUpdate(BotRef.SHARED, "{}");
    }
}
