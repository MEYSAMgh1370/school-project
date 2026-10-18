package com.example.roshdeandishe.notification.internal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * سقف روزانه‌ی پرسش‌های متن آزاد برای هر کاربر (و در صورت روشن‌بودن، برای کل سامانه) بر پایه‌ی
 * روزِ تقویمیِ تهران. شمارنده‌ها در حافظه‌اند و فقط برای یک نمونه‌ی سرویس معتبرند (فرضِ مشخصاتِ
 * طرح)؛ با ری‌استارت یا تغییر روز صفر می‌شوند. {@link #tryAcquire} همگام‌سازی شده است تا بررسی و
 * افزایشِ شمارنده‌ی کاربر و کل، زیرِ هم‌روندی بیش از سقف نپذیرد.
 */
@Component
class LlmQuota {

    private static final ZoneId TEHRAN = ZoneId.of("Asia/Tehran");

    private final LlmSettings settings;
    private final Clock clock;
    private final ConcurrentHashMap<Long, AtomicInteger> perUser = new ConcurrentHashMap<>();
    private final AtomicInteger total = new AtomicInteger();
    private LocalDate day;

    @Autowired
    LlmQuota(LlmSettings settings) {
        this(settings, Clock.system(TEHRAN));
    }

    LlmQuota(LlmSettings settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    /**
     * یک پرسش را می‌شمارد و اگر مجاز بود true می‌دهد؛ فقط فراخوانیِ موفق شمرده می‌شود.
     * سقفِ کاربر بیشینه‌ی سقفِ نقش‌های فعال است (بدون نقش: {@code default}).
     */
    synchronized boolean tryAcquire(long userId, Collection<String> activeRoleCodes) {
        LocalDate today = LocalDate.now(clock.withZone(TEHRAN));
        if (!today.equals(day)) {
            perUser.clear();
            total.set(0);
            day = today;
        }

        int limit = limitFor(activeRoleCodes);
        int cap = settings.dailyCapTotal();
        AtomicInteger used = perUser.computeIfAbsent(userId, id -> new AtomicInteger());
        if (used.get() >= limit || (cap > 0 && total.get() >= cap)) {
            return false;
        }
        used.incrementAndGet();
        total.incrementAndGet();
        return true;
    }

    private int limitFor(Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return settings.dailyLimitFor("default");
        }
        int max = Integer.MIN_VALUE;
        for (String code : roleCodes) {
            max = Math.max(max, settings.dailyLimitFor(code));
        }
        return max;
    }
}
