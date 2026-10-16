package com.example.roshdeandishe.notification.internal;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * حافظه‌ی کوچک و محدود از آخرین {@code update_id}های دیده‌شده. بله وقتی
 * جواب وب‌هوک کند باشد همان Update را دوباره می‌فرستد؛ با این کلاس
 * تکرارها قبل از پردازش دور ریخته می‌شوند. ظرفیت که پر شود، قدیمی‌ترین
 * شناسه فراموش می‌شود (حافظه بی‌نهایت رشد نمی‌کند).
 * <p>
 * thread-safe است: بررسی و ثبت در یک عملیات synchronized انجام می‌شود، پس
 * دو درخواست هم‌زمان با یک شناسه فقط یک‌بار «جدید» حساب می‌شوند.
 */
final class RecentUpdateIds {

    private final Map<Long, Boolean> ids;

    RecentUpdateIds(int capacity) {
        this.ids = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
                return size() > capacity;
            }
        };
    }

    /** true بار اول که شناسه دیده می‌شود؛ false تا وقتی در میان {@code capacity} شناسه‌ی آخر است. */
    boolean markSeen(long updateId) {
        synchronized (ids) {
            return ids.put(updateId, Boolean.TRUE) == null;
        }
    }
}
