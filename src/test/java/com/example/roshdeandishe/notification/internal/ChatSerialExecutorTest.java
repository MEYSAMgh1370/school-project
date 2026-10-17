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
import java.util.concurrent.atomic.AtomicBoolean;

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
        var executor = new ChatSerialExecutor<Long>(pool, 100, 100, k -> null);
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
        var executor = new ChatSerialExecutor<Long>(pool, 100, 100, k -> null);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch otherDone = new CountDownLatch(1);
        executor.execute(1L, () -> await(release));
        executor.execute(2L, otherDone::countDown);

        assertThat(otherDone.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        release.countDown();
    }

    @Test
    void nullKeyRunsWithoutOrdering() throws Exception {
        var executor = new ChatSerialExecutor<Long>(pool, 100, 100, k -> null);
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
        var executor = new ChatSerialExecutor<Long>(pool, 2, 2, k -> null);
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
        var executor = new ChatSerialExecutor<Long>(pool, 100, 100, k -> null);
        CountDownLatch secondRan = new CountDownLatch(1);
        executor.execute(1L, () -> {
            throw new IllegalStateException("boom");
        });
        executor.execute(1L, secondRan::countDown);

        assertThat(secondRan.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void maxPendingIsGlobalAcrossKeysAndARejectedTaskNeverRuns() throws Exception {
        var executor = new ChatSerialExecutor<Long>(pool, 2, 2, k -> null);
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        for (long key : new long[] {1L, 2L}) {
            executor.execute(key, () -> {
                started.countDown();
                await(release);
            });
        }
        assertThat(started.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        CountDownLatch queuedRan = new CountDownLatch(2);
        executor.execute(1L, queuedRan::countDown);
        executor.execute(2L, queuedRan::countDown);

        AtomicBoolean rejectedRan = new AtomicBoolean();
        assertThatThrownBy(() -> executor.execute(1L, () -> rejectedRan.set(true)))
                .isInstanceOf(RejectedExecutionException.class);
        assertThatThrownBy(() -> executor.execute(3L, () -> rejectedRan.set(true)))
                .isInstanceOf(RejectedExecutionException.class);
        release.countDown();
        assertThat(queuedRan.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        // بعد از اجرای کارهای منتظر، ظرفیت دوباره آزاد است
        CountDownLatch after = new CountDownLatch(1);
        executor.execute(1L, after::countDown);
        assertThat(after.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(rejectedRan).isFalse();
    }

    @Test
    void anErrorFromATaskDoesNotStallTheChat() throws Exception {
        var executor = new ChatSerialExecutor<Long>(pool, 100, 100, k -> null);
        CountDownLatch secondRan = new CountDownLatch(1);
        executor.execute(1L, () -> {
            throw new AssertionError("boom");
        });
        executor.execute(1L, secondRan::countDown);

        assertThat(secondRan.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void anErrorFromThePoolLeavesTheKeyUsable() throws Exception {
        AtomicBoolean fail = new AtomicBoolean(true);
        var executor = new ChatSerialExecutor<Long>(task -> {
            if (fail.getAndSet(false)) {
                throw new NoClassDefFoundError("boom");
            }
            pool.execute(task);
        }, 1, 1, k -> null);
        assertThatThrownBy(() -> executor.execute(1L, () -> { })).isInstanceOf(NoClassDefFoundError.class);

        CountDownLatch ran = new CountDownLatch(1);
        executor.execute(1L, ran::countDown);
        assertThat(ran.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void oneGroupCannotExceedItsShare() throws Exception {
        var executor = new ChatSerialExecutor<Key>(pool, 100, 40, Key::bot);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        executor.execute(new Key(1L, 1L), () -> {
            started.countDown();
            await(release);
        });
        assertThat(started.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        // یک کار در حال اجراست و دیگر منتظر شمرده نمی‌شود؛ ۴۰ کار منتظر سهم کامل گروه است
        for (int i = 0; i < 40; i++) {
            executor.execute(new Key(1L, 1L), () -> { });
        }

        AtomicBoolean rejectedRan = new AtomicBoolean();
        assertThatThrownBy(() -> executor.execute(new Key(1L, 2L), () -> rejectedRan.set(true)))
                .isInstanceOf(RejectedExecutionException.class);
        CountDownLatch otherRan = new CountDownLatch(1);
        executor.execute(new Key(2L, 1L), otherRan::countDown);
        release.countDown();
        assertThat(otherRan.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(rejectedRan).isFalse();
    }

    @Test
    void groupCountIsReleasedWhenTasksRun() throws Exception {
        var executor = new ChatSerialExecutor<Key>(pool, 100, 2, Key::bot);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        executor.execute(new Key(1L, 1L), () -> {
            started.countDown();
            await(release);
        });
        assertThat(started.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        CountDownLatch queuedRan = new CountDownLatch(2);
        executor.execute(new Key(1L, 1L), queuedRan::countDown);
        executor.execute(new Key(1L, 1L), queuedRan::countDown);
        assertThatThrownBy(() -> executor.execute(new Key(1L, 1L), () -> { }))
                .isInstanceOf(RejectedExecutionException.class);
        release.countDown();
        assertThat(queuedRan.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        CountDownLatch after = new CountDownLatch(1);
        executor.execute(new Key(1L, 1L), after::countDown);
        assertThat(after.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void sameChatInTwoGroupsRunsIndependently() throws Exception {
        var executor = new ChatSerialExecutor<Key>(pool, 100, 100, Key::bot);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch otherDone = new CountDownLatch(1);
        executor.execute(new Key(1L, 5L), () -> await(release));
        executor.execute(new Key(2L, 5L), otherDone::countDown);

        assertThat(otherDone.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        release.countDown();
    }

    private record Key(Long bot, Long chat) {
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
