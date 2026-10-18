package com.example.roshdeandishe.notification.internal;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LlmQuotaTest {

    private static final ZoneId TEHRAN = ZoneId.of("Asia/Tehran");

    /** ساعتِ قابل‌تغییر برای تست. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(ZonedDateTime start) {
            this.now = start.toInstant();
        }

        void set(ZonedDateTime t) {
            this.now = t.toInstant();
        }

        @Override
        public ZoneId getZone() {
            return TEHRAN;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static MutableClock clockAt(int day, int hour, int minute) {
        return new MutableClock(ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, TEHRAN));
    }

    private static MockEnvironment env() {
        return new MockEnvironment()
                .withProperty("roshdeandishe.bot.llm.daily-limit.default", "30")
                .withProperty("roshdeandishe.bot.llm.daily-limit.TEACHER", "30")
                .withProperty("roshdeandishe.bot.llm.daily-limit.SCHOOL_ADMIN", "100");
    }

    private static LlmQuota quota(MockEnvironment env, Clock clock) {
        return new LlmQuota(new LlmSettings(env), clock);
    }

    @Test
    void limitIsHighestOfActiveRoles() {
        LlmQuota quota = quota(env(), clockAt(18, 10, 0));
        List<String> roles = List.of("TEACHER", "SCHOOL_ADMIN");
        for (int i = 0; i < 100; i++) {
            assertThat(quota.tryAcquire(1L, roles)).as("call %d", i + 1).isTrue();
        }
        assertThat(quota.tryAcquire(1L, roles)).isFalse();
    }

    @Test
    void customRoleUsesDefault() {
        LlmQuota quota = quota(env(), clockAt(18, 10, 0));
        Set<String> roles = Set.of("FINANCE_ONLY");
        for (int i = 0; i < 30; i++) {
            assertThat(quota.tryAcquire(1L, roles)).as("call %d", i + 1).isTrue();
        }
        assertThat(quota.tryAcquire(1L, roles)).isFalse();
    }

    @Test
    void noRolesUsesDefault() {
        LlmQuota quota = quota(env(), clockAt(18, 10, 0));
        for (int i = 0; i < 30; i++) {
            assertThat(quota.tryAcquire(1L, List.of())).isTrue();
        }
        assertThat(quota.tryAcquire(1L, List.of())).isFalse();
    }

    @Test
    void usersAreCountedSeparately() {
        LlmQuota quota = quota(env(), clockAt(18, 10, 0));
        for (int i = 0; i < 30; i++) {
            quota.tryAcquire(1L, List.of());
        }
        assertThat(quota.tryAcquire(1L, List.of())).isFalse();
        assertThat(quota.tryAcquire(2L, List.of())).isTrue();
    }

    @Test
    void resetsOnTehranDayBoundary() {
        MutableClock clock = clockAt(18, 23, 59);
        LlmQuota quota = quota(env(), clock);
        for (int i = 0; i < 30; i++) {
            assertThat(quota.tryAcquire(1L, List.of())).isTrue();
        }
        assertThat(quota.tryAcquire(1L, List.of())).isFalse();

        clock.set(ZonedDateTime.of(2026, 10, 19, 0, 1, 0, 0, TEHRAN));
        assertThat(quota.tryAcquire(1L, List.of())).isTrue();
    }

    @Test
    void dayBoundaryFollowsTehranNotUtc() {
        // ۲۰:۳۰ UTC در ۱۸ اکتبر = ۰۰:۰۰ تهران در ۱۹ اکتبر؛ مرزِ UTC هنوز نگذشته است.
        MutableClock clock = new MutableClock(ZonedDateTime.of(2026, 10, 18, 20, 29, 0, 0, ZoneOffset.UTC));
        LlmQuota quota = quota(env(), clock);
        for (int i = 0; i < 30; i++) {
            quota.tryAcquire(1L, List.of());
        }
        assertThat(quota.tryAcquire(1L, List.of())).isFalse();
        clock.set(ZonedDateTime.of(2026, 10, 18, 20, 31, 0, 0, ZoneOffset.UTC));
        assertThat(quota.tryAcquire(1L, List.of())).isTrue();
    }

    @Test
    void totalCapAppliesAcrossUsers() {
        MockEnvironment env = env().withProperty("roshdeandishe.bot.llm.daily-cap-total", "2");
        LlmQuota quota = quota(env, clockAt(18, 10, 0));
        assertThat(quota.tryAcquire(1L, List.of())).isTrue();
        assertThat(quota.tryAcquire(2L, List.of())).isTrue();
        assertThat(quota.tryAcquire(3L, List.of())).isFalse();
    }

    @Test
    void refusedCallDoesNotConsumeQuota() {
        MockEnvironment env = env().withProperty("roshdeandishe.bot.llm.daily-cap-total", "1");
        LlmQuota quota = quota(env, clockAt(18, 10, 0));
        assertThat(quota.tryAcquire(1L, List.of())).isTrue();
        // کاربر ۲ به‌خاطر سقفِ کل رد می‌شود و نباید از سهمِ خودش کم شود.
        for (int i = 0; i < 5; i++) {
            assertThat(quota.tryAcquire(2L, List.of())).isFalse();
        }
    }

    @Test
    void zeroTotalCapMeansUnlimited() {
        MockEnvironment env = env().withProperty("roshdeandishe.bot.llm.daily-cap-total", "0");
        LlmQuota quota = quota(env, clockAt(18, 10, 0));
        for (long user = 1; user <= 50; user++) {
            assertThat(quota.tryAcquire(user, List.of("TEACHER"))).isTrue();
        }
    }

    @Test
    void concurrentCallsNeverOverAdmit() throws Exception {
        MockEnvironment env = env().withProperty("roshdeandishe.bot.llm.daily-cap-total", "40");
        LlmQuota quota = quota(env, clockAt(18, 10, 0));
        int threads = 16;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.atomic.AtomicInteger admitted = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < 20; i++) {
                    if (quota.tryAcquire(7L, List.of())) {
                        admitted.incrementAndGet();
                    }
                }
                return null;
            }));
        }
        start.countDown();
        for (var f : futures) {
            f.get();
        }
        pool.shutdown();
        // سقفِ کاربر ۳۰ است و سقفِ کل ۴۰؛ کمینه‌ی این دو، یعنی ۳۰، برقرار می‌ماند.
        assertThat(admitted.get()).isEqualTo(30);
    }
}
