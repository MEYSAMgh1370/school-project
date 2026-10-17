package com.example.roshdeandishe.notification.internal;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SchoolBotActivityTest {

    private static final Instant T0 = Instant.parse("2026-10-17T10:00:00Z");

    private static class MutableClock extends Clock {
        Instant now = T0;

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final SchoolBotRepository bots = mock(SchoolBotRepository.class);
    private final MutableClock clock = new MutableClock();
    private final SchoolBotActivity activity = new SchoolBotActivity(bots, clock);

    @Test
    void writesAtMostOncePerMinutePerBot() {
        BotRef bot = BotRef.school(3, 7);

        activity.touch(bot);
        clock.now = T0.plusSeconds(30);
        activity.touch(bot);
        clock.now = T0.plusSeconds(59);
        activity.touch(bot);

        verify(bots, times(1)).touchLastUpdate(3L, T0);

        clock.now = T0.plusSeconds(61);
        activity.touch(bot);

        verify(bots, times(1)).touchLastUpdate(3L, T0.plusSeconds(61));
        verify(bots, times(2)).touchLastUpdate(eq(3L), any());
    }

    @Test
    void botsAreThrottledIndependently() {
        activity.touch(BotRef.school(3, 7));
        activity.touch(BotRef.school(4, 8));

        verify(bots).touchLastUpdate(3L, T0);
        verify(bots).touchLastUpdate(4L, T0);
    }

    @Test
    void sharedBotIsNoOp() {
        activity.touch(BotRef.SHARED);

        verifyNoInteractions(bots);
    }
}
