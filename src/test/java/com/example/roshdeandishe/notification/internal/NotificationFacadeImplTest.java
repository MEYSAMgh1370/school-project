package com.example.roshdeandishe.notification.internal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * تست واحدِ fan-out ی {@link NotificationFacadeImpl}: بدون Spring context و
 * بدون دیتابیس، با کانال‌های جعلی.
 */
class NotificationFacadeImplTest {

    /** کانالی که هر پیام را ثبت می‌کند و نتیجه‌ی ازپیش‌تعیین‌شده برمی‌گرداند. */
    private static final class RecordingChannel implements NotificationChannel {
        private final boolean result;
        private final RuntimeException toThrow;
        final List<String> received = new ArrayList<>();

        RecordingChannel(boolean result) {
            this(result, null);
        }

        RecordingChannel(boolean result, RuntimeException toThrow) {
            this.result = result;
            this.toThrow = toThrow;
        }

        @Override
        public boolean sendToUser(Long userId, String text) {
            received.add(userId + ":" + text);
            if (toThrow != null) {
                throw toThrow;
            }
            return result;
        }
    }

    @Test
    void everyChannelReceivesTheMessage() {
        RecordingChannel first = new RecordingChannel(true);
        RecordingChannel second = new RecordingChannel(true);

        boolean delivered = new NotificationFacadeImpl(List.of(first, second)).sendToUser(7L, "hello");

        assertThat(delivered).isTrue();
        assertThat(first.received).containsExactly("7:hello");
        assertThat(second.received).containsExactly("7:hello");
    }

    @Test
    void aChannelReturningFalseDoesNotStopTheOthers() {
        RecordingChannel failing = new RecordingChannel(false);
        RecordingChannel working = new RecordingChannel(true);

        boolean delivered = new NotificationFacadeImpl(List.of(failing, working)).sendToUser(7L, "hello");

        assertThat(failing.received).hasSize(1);
        assertThat(working.received).containsExactly("7:hello");
        assertThat(delivered).isTrue();
    }

    @Test
    void aChannelThrowingIsContainedAndDoesNotStopTheOthers() {
        RecordingChannel throwing = new RecordingChannel(true, new IllegalStateException("boom"));
        RecordingChannel working = new RecordingChannel(true);

        boolean delivered = new NotificationFacadeImpl(List.of(throwing, working)).sendToUser(7L, "hello");

        assertThat(throwing.received).hasSize(1);
        assertThat(working.received).containsExactly("7:hello");
        assertThat(delivered).isTrue();
    }

    @Test
    void returnsFalseWhenNoChannelDelivers() {
        RecordingChannel failing = new RecordingChannel(false);
        RecordingChannel throwing = new RecordingChannel(true, new IllegalStateException("boom"));

        boolean delivered = new NotificationFacadeImpl(List.of(failing, throwing)).sendToUser(7L, "hello");

        assertThat(delivered).isFalse();
    }
}
