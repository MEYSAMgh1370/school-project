package com.example.roshdeandishe.notification.internal;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationAttemptsTest {

    private static final class MutableClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-16T10:00:00Z"));

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    @Test
    void tenPerHourPerSchool() {
        MutableClock clock = new MutableClock();
        RegistrationAttempts attempts = new RegistrationAttempts(clock);

        for (int i = 0; i < 10; i++) {
            assertThat(attempts.tryAcquire(1L)).isTrue();
        }
        assertThat(attempts.tryAcquire(1L)).isFalse();
        assertThat(attempts.tryAcquire(2L)).isTrue();

        clock.now.set(clock.now.get().plusSeconds(3601));
        assertThat(attempts.tryAcquire(1L)).isTrue();
    }
}
