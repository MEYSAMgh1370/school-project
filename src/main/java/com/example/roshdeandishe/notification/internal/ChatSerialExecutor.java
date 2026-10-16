package com.example.roshdeandishe.notification.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * اجرای ترتیبی کارها به‌ازای هر کلید (اینجا شناسه‌ی چت) روی یک pool مشترک:
 * کارهای هم‌کلید یکی‌یکی و به ترتیب ارسال اجرا می‌شوند، ولی کلیدهای
 * مختلف می‌توانند موازی بروند. خودش thread نمی‌سازد و یک Bean هم نیست
 * (Bean از نوع Executor جلوی {@code applicationTaskExecutor} را می‌گیرد).
 * <p>
 * اولین کار یک کلید صفی برای آن کلید می‌سازد و یک job «تخلیه» به pool
 * می‌سپارد؛ آن job تا خالی شدن صف کارها را یکی‌یکی برمی‌دارد و اجرا می‌کند
 * و آخر سر کلید را از map حذف می‌کند. کار بعدیِ یک کلید مشغول فقط به صف
 * اضافه می‌شود. بررسی خالی بودن صف و حذف کلید زیر همان قفلی است که
 * افزودن کار می‌گیرد، پس کاری گم نمی‌شود.
 */
final class ChatSerialExecutor {

    private static final Logger log = LoggerFactory.getLogger(ChatSerialExecutor.class);

    private final Executor pool;
    private final int maxPending;
    private final Map<Long, ArrayDeque<Runnable>> queues = new HashMap<>();
    /** تعداد کل کارهای منتظر شروع در همه‌ی چت‌ها؛ فقط زیر قفل {@code queues} تغییر می‌کند. */
    private int pending;

    ChatSerialExecutor(Executor pool, int maxPending) {
        this.pool = pool;
        this.maxPending = maxPending;
    }

    /**
     * کارهای هم‌کلید یکی‌یکی و به ترتیب ارسال اجرا می‌شوند؛ کلیدهای مختلف
     * ممکن است موازی بروند. {@code key == null} بدون ترتیب اجرا می‌شود.
     * اگر {@code maxPending} کار (در مجموع همه‌ی کلیدها) منتظر شروع باشد یا pool رد کند
     * {@link RejectedExecutionException} می‌دهد و کار رد شده هرگز اجرا نمی‌شود.
     */
    void execute(Long key, Runnable task) {
        if (key == null) {
            pool.execute(task);
            return;
        }
        synchronized (queues) {
            if (pending >= maxPending) {
                throw new RejectedExecutionException("صف کارها پر است");
            }
            ArrayDeque<Runnable> queue = queues.get(key);
            if (queue != null) {
                queue.add(task);
                pending++;
                return;
            }
            queue = new ArrayDeque<>();
            queue.add(task);
            queues.put(key, queue);
            pending++;
            try {
                pool.execute(() -> drain(key));
            } catch (Throwable t) {
                // حتی Error (مثلاً نشد thread ساخت): کلید نماند تا چت برای همیشه گیر نکند
                queues.remove(key);
                pending--;
                throw t;
            }
        }
    }

    /** روی thread کارگر: تا خالی شدن صفِ کلید کارها را به ترتیب اجرا می‌کند. */
    private void drain(Long key) {
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
            }
            try {
                next.run();
            } catch (Throwable e) {
                log.warn("کار چت {} شکست خورد؛ کارهای بعدی همان چت ادامه می‌یابند.", key, e);
            }
        }
    }
}
