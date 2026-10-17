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
 * <p>
 * کلید عمومی است تا بتوان شناسه را همراه ربات نگه داشت (مثلاً جفت ربات و
 * {@code update_id}): یک شناسه‌ی یکسان در دو ربات تکراری حساب نمی‌شود.
 */
final class RecentUpdateIds<K> {

    private final Map<K, Boolean> ids;

    RecentUpdateIds(int capacity) {
        this.ids = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, Boolean> eldest) {
                return size() > capacity;
            }
        };
    }

    /** true بار اول که شناسه دیده می‌شود؛ false تا وقتی در میان {@code capacity} شناسه‌ی آخر است. */
    boolean markSeen(K key) {
        synchronized (ids) {
            return ids.put(key, Boolean.TRUE) == null;
        }
    }

    /** شناسه را فراموش می‌کند تا اگر دوباره رسید، دوباره «جدید» حساب شود (مثلاً وقتی پردازشش رد شد). */
    void forget(K key) {
        synchronized (ids) {
            ids.remove(key);
        }
    }
}
