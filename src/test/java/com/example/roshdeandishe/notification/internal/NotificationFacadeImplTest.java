package com.example.roshdeandishe.notification.internal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static NotificationFacadeImpl facade(NotificationChannel... channels) {
        // webhookService در مسیر sendMessageToUser استفاده نمی‌شود
        return new NotificationFacadeImpl(List.of(channels), null);
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
}
