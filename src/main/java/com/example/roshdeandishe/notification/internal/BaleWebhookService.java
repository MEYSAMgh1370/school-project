package com.example.roshdeandishe.notification.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * پارس کردن بدنه‌ی خام وب‌هوک + صدا زدن BaleUpdateHandler. جدا از
 * BaleWebhookController نگه داشته شده تا BaleUpdate (یک نوع internal)
 * هرگز به بیرون از پکیج internal درز نکند - کنترلر (در ریشه‌ی ماژول، طبق
 * قرارداد پروژه) فقط رشته‌ی خام JSON را پاس می‌دهد.
 * <p>
 * عمداً یک {@code ObjectMapper} مستقل می‌سازد، نه یکی از Spring تزریق
 * می‌کند: چون spring-modulith-events-jackson هم Jackson 3 (tools.jackson)
 * را روی classpath می‌آورد، auto-configuration جکسون در این پروژه دیگر
 * یک Bean واحد و بدون‌ابهام از نوع {@code com.fasterxml.jackson.databind.ObjectMapper}
 * نمی‌سازد. برای پارس یک DTO ساده، customization سراسری جکسون لازم
 * نیست، پس این وابستگی مبهم را دور می‌زنیم.
 * <p>
 * پردازش Update ناهمگام است: بدنه روی thread درخواست پارس می‌شود، سپس
 * handler روی یک pool خصوصی و محدود اجرا می‌شود تا وب‌هوک بدون انتظار ۲۰۰
 * بدهد (بله Updateهای کند را دوباره می‌فرستد). Updateی که {@code update_id}
 * تکراری دارد دور ریخته می‌شود؛ Update بدون {@code update_id} همیشه
 * پردازش می‌شود. عمداً pool یک Bean از نوع Executor/TaskExecutor نیست، چون
 * Spring Boot در آن صورت {@code applicationTaskExecutor} (مورد نیاز
 * listenerهای رویداد ناهمگام) را نمی‌سازد.
 */
@Component
class BaleWebhookService {

    private static final Logger log = LoggerFactory.getLogger(BaleWebhookService.class);

    private static final int CORE_THREADS = 2;
    private static final int MAX_THREADS = 4;
    private static final int QUEUE_CAPACITY = 100;
    private static final int RECENT_IDS_CAPACITY = 1000;
    private static final long SHUTDOWN_WAIT_SECONDS = 10;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BaleUpdateHandler updateHandler;
    private final Executor executor;
    private final RecentUpdateIds recentUpdateIds;
    /** فقط وقتی pool مال خود سرویس است پر می‌شود (در تست‌ها null). */
    private final ThreadPoolExecutor ownedPool;

    @Autowired
    BaleWebhookService(BaleUpdateHandler updateHandler) {
        this.updateHandler = updateHandler;
        AtomicInteger threadNumber = new AtomicInteger();
        this.ownedPool = new ThreadPoolExecutor(
                CORE_THREADS, MAX_THREADS, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> new Thread(runnable, "bale-update-" + threadNumber.incrementAndGet()),
                new ThreadPoolExecutor.AbortPolicy());
        this.executor = ownedPool;
        this.recentUpdateIds = new RecentUpdateIds(RECENT_IDS_CAPACITY);
    }

    /** برای تست: Executor و حافظه‌ی شناسه‌ها از بیرون داده می‌شوند. */
    BaleWebhookService(BaleUpdateHandler updateHandler, Executor executor, RecentUpdateIds recentUpdateIds) {
        this.updateHandler = updateHandler;
        this.executor = executor;
        this.recentUpdateIds = recentUpdateIds;
        this.ownedPool = null;
    }

    /**
     * پارس روی thread فراخوان، حذف تکراری‌ها و سپردن handler به pool. عمداً
     * هر Exception را اینجا می‌بلعد: یک وب‌هوک باید همیشه سریع ۲۰۰
     * برگرداند - یک 500 باعث می‌شود سرور بله دوباره و دوباره همان Update
     * را retry کند، که مشکل اصلی را حل نمی‌کند و فقط لاگ را شلوغ می‌کند.
     * اگر pool پر باشد Update با یک لاگ دور ریخته می‌شود (هرگز بلاک نمی‌شود).
     */
    void processRawUpdate(String rawBody) {
        Long updateId = null;
        try {
            BaleUpdate update = objectMapper.readValue(rawBody, BaleUpdate.class);
            updateId = update.updateId();
            if (update.updateId() != null && !recentUpdateIds.markSeen(update.updateId())) {
                log.debug("Update تکراری از بله دور ریخته شد: {}", update.updateId());
                return;
            }
            executor.execute(() -> handleSafely(update));
        } catch (RejectedExecutionException e) {
            // پردازش نشده، پس «دیده‌شده» هم نماند: اگر همین Update دوباره رسید، دور ریخته نشود
            if (updateId != null) {
                recentUpdateIds.forget(updateId);
            }
            log.warn("Update {} از بله دور ریخته شد؛ صف پر است یا pool خاموش شده (خاموش={}).",
                    updateId, executor instanceof ExecutorService pool && pool.isShutdown());
        } catch (Exception e) {
            log.warn("پردازش Update ورودی از بله شکست خورد.", e);
        }
    }

    /** روی thread کارگر اجرا می‌شود؛ خطا فقط لاگ می‌شود، مثل قبل. */
    private void handleSafely(BaleUpdate update) {
        try {
            updateHandler.handle(update);
        } catch (Exception e) {
            log.warn("پردازش Update ورودی از بله شکست خورد.", e);
        }
    }

    @PreDestroy
    void shutdown() {
        if (ownedPool == null) {
            return;
        }
        ownedPool.shutdown();
        try {
            if (!ownedPool.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                ownedPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            ownedPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
