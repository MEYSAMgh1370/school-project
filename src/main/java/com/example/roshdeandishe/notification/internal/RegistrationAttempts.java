package com.example.roshdeandishe.notification.internal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * سقف تلاش برای ثبت ربات: حداکثر ۱۰ تلاش در هر مدرسه در پنجره‌ی لغزنده‌ی ۳۶۰۰ ثانیه‌ای، در حافظه
 * (با ری‌استارت صفر می‌شود؛ هدف فقط جلوگیری از حدس‌زدن/سوءاستفاده از getMe است).
 */
@Component
class RegistrationAttempts {

    static final int MAX_ATTEMPTS = 10;
    static final Duration WINDOW = Duration.ofSeconds(3600);

    private final Clock clock;
    private final ConcurrentMap<Long, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    @Autowired
    RegistrationAttempts() {
        this(Clock.systemUTC());
    }

    RegistrationAttempts(Clock clock) {
        this.clock = clock;
    }

    /** یک تلاش را ثبت می‌کند؛ اگر سقف پر باشد false (و چیزی ثبت نمی‌شود). */
    boolean tryAcquire(Long schoolId) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(WINDOW);
        Deque<Instant> window = attempts.computeIfAbsent(schoolId, id -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && !window.peekFirst().isAfter(cutoff)) {
                window.pollFirst();
            }
            if (window.size() >= MAX_ATTEMPTS) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }
}
