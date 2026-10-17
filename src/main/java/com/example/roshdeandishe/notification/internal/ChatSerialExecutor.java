package com.example.roshdeandishe.notification.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;

/**
 * اجرای ترتیبی کارها به‌ازای هر کلید (مثلاً چت یک ربات) روی یک pool مشترک:
 * کارهای هم‌کلید یکی‌یکی و به ترتیب ارسال اجرا می‌شوند، ولی کلیدهای
 * مختلف می‌توانند موازی بروند. خودش thread نمی‌سازد و یک Bean هم نیست
 * (Bean از نوع Executor جلوی {@code applicationTaskExecutor} را می‌گیرد).
 * <p>
 * اولین کار یک کلید صفی برای آن کلید می‌سازد و یک job «تخلیه» به pool
 * می‌سپارد؛ آن job تا خالی شدن صف کارها را یکی‌یکی برمی‌دارد و اجرا می‌کند
 * و آخر سر کلید را از map حذف می‌کند. کار بعدیِ یک کلید مشغول فقط به صف
 * اضافه می‌شود. بررسی خالی بودن صف و حذف کلید زیر همان قفلی است که
 * افزودن کار می‌گیرد، پس کاری گم نمی‌شود.
 * <p>
 * علاوه بر سقف کل، هر «گروه» (مثلاً یک ربات) سهم محدودی از کارهای منتظر دارد
 * تا یک گروه شلوغ جای بقیه را نگیرد. گروه از روی کلید با تابع {@code group}
 * به دست می‌آید و {@code null} هم یک گروه معتبر است.
 */
final class ChatSerialExecutor<K> {

    private static final Logger log = LoggerFactory.getLogger(ChatSerialExecutor.class);

    private final Executor pool;
    private final int maxPending;
    private final int maxPendingPerGroup;
    private final Function<K, ?> group;
    private final Map<K, ArrayDeque<Runnable>> queues = new HashMap<>();
    /** تعداد کارهای منتظر شروع به‌ازای هر گروه (ورودی صفر حذف می‌شود)؛ زیر همان قفل {@code queues}. */
    private final Map<Object, Integer> pendingPerGroup = new HashMap<>();
    /** تعداد کل کارهای منتظر شروع در همه‌ی چت‌ها؛ فقط زیر قفل {@code queues} تغییر می‌کند. */
    private int pending;

    ChatSerialExecutor(Executor pool, int maxPending, int maxPendingPerGroup, Function<K, ?> group) {
        this.pool = pool;
        this.maxPending = maxPending;
        this.maxPendingPerGroup = maxPendingPerGroup;
        this.group = group;
    }

    /**
     * کارهای هم‌کلید یکی‌یکی و به ترتیب ارسال اجرا می‌شوند؛ کلیدهای مختلف
     * ممکن است موازی بروند. {@code key == null} بدون ترتیب اجرا می‌شود.
     * اگر {@code maxPending} کار (در مجموع همه‌ی کلیدها) یا {@code maxPendingPerGroup} کارِ
     * همان گروه منتظر شروع باشد، یا pool رد کند
     * {@link RejectedExecutionException} می‌دهد و کار رد شده هرگز اجرا نمی‌شود.
     */
    void execute(K key, Runnable task) {
        if (key == null) {
            pool.execute(task);
            return;
        }
        synchronized (queues) {
            Object groupKey = group.apply(key);
            if (pending >= maxPending || pendingPerGroup.getOrDefault(groupKey, 0) >= maxPendingPerGroup) {
                throw new RejectedExecutionException("صف کارها پر است");
            }
            ArrayDeque<Runnable> queue = queues.get(key);
            if (queue != null) {
                queue.add(task);
                pending++;
                pendingPerGroup.merge(groupKey, 1, Integer::sum);
                return;
            }
            queue = new ArrayDeque<>();
            queue.add(task);
            queues.put(key, queue);
            pending++;
            pendingPerGroup.merge(groupKey, 1, Integer::sum);
            try {
                pool.execute(() -> drain(key));
            } catch (Throwable t) {
                // حتی Error (مثلاً نشد thread ساخت): کلید نماند تا چت برای همیشه گیر نکند
                queues.remove(key);
                pending--;
                releaseGroup(groupKey);
                throw t;
            }
        }
    }

    /** روی thread کارگر: تا خالی شدن صفِ کلید کارها را به ترتیب اجرا می‌کند. */
    private void drain(K key) {
        while (true) {
            Runnable next;
            synchronized (queues) {
                ArrayDeque<Runnable> queue = queues.get(key);
                next = queue.poll();
                if (next == null) {
                    queues.remove(key);
                    return;
                }
                pending--;
                releaseGroup(group.apply(key));
            }
            try {
                next.run();
            } catch (Throwable e) {
                log.warn("کار چت {} شکست خورد؛ کارهای بعدی همان چت ادامه می‌یابند.", key, e);
            }
        }
    }

    /** یکی از شمارنده‌ی گروه کم می‌کند و ورودی صفر را حذف می‌کند؛ زیر قفل {@code queues} صدا زده شود. */
    private void releaseGroup(Object groupKey) {
        pendingPerGroup.computeIfPresent(groupKey, (g, n) -> n > 1 ? n - 1 : null);
    }
}
