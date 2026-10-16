package com.example.roshdeandishe.notification.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatSerialExecutorTest {

    private static final long WAIT_SECONDS = 5;

    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    @Test
    void sameKeyRunsInSubmissionOrder() throws Exception {
        var executor = new ChatSerialExecutor(pool, 100);
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(50);
        for (int i = 0; i < 50; i++) {
            int n = i;
            executor.execute(1L, () -> {
                order.add(n);
                done.countDown();
            });
        }

        assertThat(done.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            expected.add(i);
        }
        assertThat(order).isEqualTo(expected);
    }

    @Test
    void differentKeysRunInParallel() throws Exception {
        var executor = new ChatSerialExecutor(pool, 100);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch otherDone = new CountDownLatch(1);
        executor.execute(1L, () -> await(release));
        executor.execute(2L, otherDone::countDown);

        assertThat(otherDone.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        release.countDown();
    }

    @Test
    void nullKeyRunsWithoutOrdering() throws Exception {
        var executor = new ChatSerialExecutor(pool, 100);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch bothRan = new CountDownLatch(2);
        executor.execute(null, () -> {
            bothRan.countDown();
            await(release);
        });
        executor.execute(null, bothRan::countDown);

        assertThat(bothRan.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        release.countDown();
    }

    @Test
    void overMaxPendingIsRejected() throws Exception {
        var executor = new ChatSerialExecutor(pool, 2);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        executor.execute(1L, () -> {
            started.countDown();
            await(release);
        });
        assertThat(started.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        executor.execute(1L, () -> { });
        executor.execute(1L, () -> { });
        assertThatThrownBy(() -> executor.execute(1L, () -> { }))
                .isInstanceOf(RejectedExecutionException.class);
        release.countDown();
    }

    @Test
    void aFailingTaskDoesNotStallTheChat() throws Exception {
        var executor = new ChatSerialExecutor(pool, 100);
        CountDownLatch secondRan = new CountDownLatch(1);
        executor.execute(1L, () -> {
            throw new IllegalStateException("boom");
        });
        executor.execute(1L, secondRan::countDown);

        assertThat(secondRan.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
