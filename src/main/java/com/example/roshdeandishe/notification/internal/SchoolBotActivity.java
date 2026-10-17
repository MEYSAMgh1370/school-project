package com.example.roshdeandishe.notification.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ثبت «آخرین Updateِ دریافتی» رباتِ مدرسه ({@code last_update_at}) برای نمایش سلامت ربات. برای اینکه هر پیام
 * یک نوشتن روی پایگاه داده نباشد، برای هر ربات حداکثر دقیقه‌ای یک بار نوشته می‌شود؛ زمانِ آخرین نوشتن در
 * حافظه نگه داشته می‌شود (با راه‌اندازی دوباره، فقط یک نوشتنِ اضافه رخ می‌دهد). ربات مشترک ردیفی ندارد و
 * بی‌اثر است.
 */
@Component
class SchoolBotActivity {

    private static final Logger log = LoggerFactory.getLogger(SchoolBotActivity.class);
    private static final Duration MIN_INTERVAL = Duration.ofSeconds(60);

    private final SchoolBotRepository bots;
    private final Clock clock;
    private final ConcurrentHashMap<Long, Instant> lastWritten = new ConcurrentHashMap<>();

    @Autowired
    SchoolBotActivity(SchoolBotRepository bots) {
        this(bots, Clock.systemUTC());
    }

    /** برای آزمون: ساعتِ ثابت. */
    SchoolBotActivity(SchoolBotRepository bots, Clock clock) {
        this.bots = bots;
        this.clock = clock;
    }

    void touch(BotRef bot) {
        if (bot.isShared()) {
            return;
        }
        Instant now = clock.instant();
        boolean[] due = {false};
        lastWritten.compute(bot.botId(), (id, previous) -> {
            if (previous == null || !now.isBefore(previous.plus(MIN_INTERVAL))) {
                due[0] = true;
                return now;
            }
            return previous;
        });
        if (!due[0]) {
            return;
        }
        try {
            bots.touchLastUpdate(bot.botId(), now);
        } catch (RuntimeException e) {
            lastWritten.remove(bot.botId());
            log.warn("نوشتن last_update_at ربات {} شکست خورد: {}", bot.botId(), e.getClass().getSimpleName());
        }
    }
}
