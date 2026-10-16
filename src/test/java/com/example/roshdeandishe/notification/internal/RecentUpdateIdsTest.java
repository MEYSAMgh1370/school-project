package com.example.roshdeandishe.notification.internal;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecentUpdateIdsTest {

    @Test
    void firstSightingIsNewSecondIsNot() {
        var seen = new RecentUpdateIds(3);
        assertThat(seen.markSeen(1)).isTrue();
        assertThat(seen.markSeen(1)).isFalse();
    }

    @Test
    void forgetsTheOldestBeyondCapacity() {
        var seen = new RecentUpdateIds(2);
        seen.markSeen(1);
        seen.markSeen(2);
        seen.markSeen(3);
        assertThat(seen.markSeen(1)).isTrue();
    }

    @Test
    void aForgottenIdIsNewAgain() {
        var seen = new RecentUpdateIds(10);
        seen.markSeen(7);
        seen.forget(7);
        assertThat(seen.markSeen(7)).isTrue();
    }

    @Test
    void concurrentSameIdIsNewExactlyOnce() throws Exception {
        var seen = new RecentUpdateIds(10);
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger newCount = new AtomicInteger();
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    if (seen.markSeen(5)) {
                        newCount.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
            assertThat(newCount.get()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
