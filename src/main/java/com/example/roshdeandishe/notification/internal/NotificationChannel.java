package com.example.roshdeandishe.notification.internal;

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
}
