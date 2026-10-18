package com.example.roshdeandishe.notification;

import java.time.Instant;
import java.util.Optional;

/**
 * مدیریت رباتِ اختصاصی بله‌ی یک مدرسه: مشاهده، ثبت/جایگزینی و حذف. توکن هرگز از این رابط بیرون نمی‌آید.
 * خطاها به‌صورت {@code NotificationException} با {@code NotificationError} پرتاب می‌شوند.
 */
public interface SchoolBotFacade {

    /** ربات ثبت‌شده‌ی مدرسه، یا خالی اگر ندارد. */
    Optional<SchoolBotInfo> find(Long schoolId);

    /** ربات را با توکن داده‌شده ثبت می‌کند یا جایگزینِ رباتِ فعلیِ مدرسه می‌کند. */
    SchoolBotInfo register(Long schoolId, String token);

    /** وب‌هوک را (تا حد امکان) برمی‌دارد و ربات و پیوندهایش را حذف می‌کند. */
    void delete(Long schoolId);

    record SchoolBotInfo(String username, SchoolBotState status, long linkedMembers, Instant lastUpdateAt,
                         String lastError, Instant lastErrorAt) {
    }

    enum SchoolBotState {
        ACTIVE,
        DISABLED
    }
}
