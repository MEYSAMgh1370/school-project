package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.notification.NotificationFacade;

/**
 * یک کانال اطلاع‌رسانی خروجی (امروز فقط بله: {@link BaleChannel}). کانال تازه
 * (مثلاً SMS) فقط یک {@code @Component} است که این اینترفیس را پیاده می‌کند و
 * {@link NotificationFacadeImpl} آن را خودکار می‌شناسد - فراخوان‌کننده‌ها
 * (مثل InvoiceIssuedEventListener) فقط NotificationFacade را می‌بینند.
 * <p>
 * قرارداد، همان قرارداد best-effort قبلیِ {@code sendMessageToUser} است:
 * {@code false} یعنی کاربر روی این کانال در دسترس نیست (مثلاً حساب را لینک
 * نکرده) یا ارسال شکست خورد؛ تشخیص این دو برای فراخوان‌کننده اهمیتی ندارد و
 * پیاده‌سازی نباید برای آن exception پرتاب کند.
 */
interface NotificationChannel {

    boolean send(Long userId, String text);

    /**
     * نتیجه‌ی دقیق‌تر برای فراخوان‌کننده‌هایی که می‌توانند دوباره تلاش کنند. پیش‌فرضِ کانالی که فقط
     * {@link #send} دارد: true = SENT و false = UNDELIVERABLE (نمی‌دانیم چرا نشد، پس تلاش دوباره نمی‌کنیم).
     * کانالی که خطای موقت را می‌شناسد (مثل {@link BaleChannel}) این متد را بازنویسی می‌کند.
     */
    default NotificationFacade.DeliveryResult deliver(Long userId, String text) {
        return send(userId, text)
                ? NotificationFacade.DeliveryResult.SENT
                : NotificationFacade.DeliveryResult.UNDELIVERABLE;
    }

    /**
     * نسخه‌ی مدرسه‌آگاه: کانالی که مسیریابی بر پایه‌ی مدرسه دارد (مثل {@link BaleChannel}) آن را بازنویسی می‌کند؛
     * پیش‌فرض همان {@link #deliver(Long, String)} است و مدرسه را نادیده می‌گیرد.
     */
    default NotificationFacade.DeliveryResult deliver(Long userId, Long schoolId, String text) {
        return deliver(userId, text);
    }
}
