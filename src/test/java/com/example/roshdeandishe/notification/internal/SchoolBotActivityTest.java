package com.example.roshdeandishe.notification.internal;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    private SchoolBotEntity entity() {
        SchoolBotEntity e = new SchoolBotEntity();
        when(bots.findById(3L)).thenReturn(Optional.of(e));
        return e;
    }

    @Test
    void writesAtMostOncePerMinutePerBot() {
        SchoolBotEntity e = entity();
        BotRef bot = BotRef.school(3, 7);

        activity.touch(bot);
        clock.now = T0.plusSeconds(30);
        activity.touch(bot);
        clock.now = T0.plusSeconds(59);
        activity.touch(bot);

        verify(bots, times(1)).save(e);
        assertThat(e.getLastUpdateAt()).isEqualTo(T0);

        clock.now = T0.plusSeconds(61);
        activity.touch(bot);

        verify(bots, times(2)).save(e);
        assertThat(e.getLastUpdateAt()).isEqualTo(T0.plusSeconds(61));
    }

    @Test
    void botsAreThrottledIndependently() {
        entity();
        SchoolBotEntity other = new SchoolBotEntity();
        when(bots.findById(4L)).thenReturn(Optional.of(other));

        activity.touch(BotRef.school(3, 7));
        activity.touch(BotRef.school(4, 8));

        verify(bots).save(other);
    }

    @Test
    void sharedBotIsNoOp() {
        activity.touch(BotRef.SHARED);

        verifyNoInteractions(bots);
    }

    @Test
    void missingBotRowWritesNothing() {
        when(bots.findById(3L)).thenReturn(Optional.empty());

        activity.touch(BotRef.school(3, 7));

        verify(bots, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
