package com.example.roshdeandishe.notification.internal;

/**
 * پورتِ یک کانال اطلاع‌رسانی. دقیقاً همان قراردادِ {@code BaleFacade.sendMessageToUser}:
 * گیرنده با userId داخلی مشخص می‌شود و نشانیِ کانال (chatId، ایمیل، ...)
 * به‌صورت خصوصی داخل هر کانال حل می‌شود. Best-effort: false یعنی «در دسترس
 * نبود یا ارسال شکست خورد».
 */
interface NotificationChannel {

    boolean sendToUser(Long userId, String text);
}
